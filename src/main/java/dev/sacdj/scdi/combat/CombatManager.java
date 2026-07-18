package dev.sacdj.scdi.combat;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.disguise.DisguiseManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the tagged/untagged state machine. Deliberately event-driven, not
 * polled: each tagged player gets exactly one scheduled expiry task (cancelled
 * and rescheduled on retag), instead of a global tick loop checking everyone's
 * remaining time 20x/second the way the datapack equivalent had to.
 *
 * <p>Uses the classic Bukkit/Spigot chat API (ChatColor, spigot().sendMessage,
 * player.sendTitle) rather than Adventure - Adventure's Component/Title/
 * sendActionBar are Paper-only additions and would silently fail to compile
 * (or worse, fail to load) on plain Spigot/CraftBukkit, which defeats the
 * point of targeting the widest possible set of server software.
 */
public final class CombatManager {

    private final JavaPlugin plugin;
    private final ScdiConfig config;
    private final DisguiseManager disguiseManager;
    private final OneShotTracker oneShotTracker;

    private final Map<UUID, CombatState> tagged = new ConcurrentHashMap<>();
    private BukkitTask actionBarTask;
    private Objective belowNameObjective;

    public CombatManager(JavaPlugin plugin, ScdiConfig config, DisguiseManager disguiseManager,
                          OneShotTracker oneShotTracker) {
        this.plugin = plugin;
        this.config = config;
        this.disguiseManager = disguiseManager;
        this.oneShotTracker = oneShotTracker;
    }

    public void start() {
        // actionbar countdown only needs whole-second resolution (that's the
        // display's own precision), so this runs at 1/sec, scoped only to
        // currently-tagged players - not the whole playerbase, not 20/sec.
        actionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickActionBars, 0L, 20L);
    }

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        for (CombatState state : tagged.values()) {
            state.cancel();
        }
        tagged.clear();
        releaseBelowNameObjective();
    }

    public boolean isTagged(Player player) {
        return tagged.containsKey(player.getUniqueId());
    }

    public Set<UUID> taggedPlayers() {
        return tagged.keySet();
    }

    public void tag(Player player) {
        UUID id = player.getUniqueId();
        Instant expiresAt = Instant.now().plus(config.combatDuration());

        CombatState existing = tagged.get(id);
        boolean isFreshTag = existing == null;

        BukkitTask expiryTask = scheduleExpiry(player, config.combatDuration());

        if (existing != null) {
            if (config.retagResetsTimer()) {
                existing.reschedule(expiresAt, expiryTask);
            } else {
                // not resetting the timer, so this retag's own expiry task is
                // redundant - the original one is still ticking down correctly.
                expiryTask.cancel();
            }
        } else {
            tagged.put(id, new CombatState(expiresAt, expiryTask));
        }

        disguiseManager.lock(player);

        if (isFreshTag) {
            announceTagged(player);
        }
    }

    /** Restores a still-tagged player's real items WITHOUT ending combat -
     * called the instant a hit is about to kill them (see CombatListener),
     * racing against Bukkit's own death/drop handling the same way the
     * datapack's check_restore_before_death.mcfunction does. Two things
     * this closes: dying with a disguised firework rocket in your hand
     * would otherwise drop a worthless stick instead of the real item
     * (visible bug), and - more importantly - if reset_on_death is off, a
     * player who stays tagged through respawn would get their real gear
     * silently restored later anyway, meaning dying was a free way to
     * dodge keepInventory-off item loss entirely unless this runs first so
     * the REAL items are what's actually at risk in the death drop, same
     * as normal. Deliberately doesn't touch scdi_tag/combat state at all -
     * if they're still tagged after respawning, they simply won't have
     * anything disguised again until the next tag/retag or periodic
     * refresh re-locks whatever they're holding then. */
    public void restoreItemsBeforeDeath(Player player) {
        if (isTagged(player)) {
            disguiseManager.unlock(player);
        }
    }

    public void release(Player player) {
        CombatState state = tagged.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.cancel();
        disguiseManager.unlock(player);
        oneShotTracker.onCombatEnd(player);
        if (belowNameObjective != null) {
            belowNameObjective.getScore(player.getName()).resetScore();
        }
        announceReleased(player);
    }

    private BukkitTask scheduleExpiry(Player player, Duration duration) {
        long ticks = Math.max(1, duration.toMillis() / 50);
        return plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                release(player);
            } else {
                tagged.remove(player.getUniqueId());
            }
        }, ticks);
    }

    private void tickActionBars() {
        updateBelowNameObjective();
        if (tagged.isEmpty() || !config.showActionBar()) {
            return;
        }
        long totalMs = Math.max(1, config.combatDuration().toMillis());
        for (UUID id : tagged.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) {
                continue;
            }
            CombatState state = tagged.get(id);
            if (state == null) {
                continue;
            }
            long elapsedMs = totalMs - state.millisRemaining();
            ChatColor color = phaseColor(elapsedMs, totalMs);
            sendActionBar(player, color + "⚔ In Combat (" + state.secondsRemaining() + "s)");
        }
    }

    /** below_name is a single GLOBAL scoreboard slot shared by the whole
     * server, same caveat the datapack's own show_timer_above_head has - this
     * only claims it while the setting is on and hands it back (clears
     * instead of restoring some other prior use, unlike the datapack's
     * belowname-restore-objective - this plugin doesn't yet track what, if
     * anything, was using the slot before it) the instant it's turned off. */
    private void updateBelowNameObjective() {
        if (!config.showTimerAboveHead()) {
            releaseBelowNameObjective();
            return;
        }
        if (belowNameObjective == null) {
            var scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
            Objective existing = scoreboard.getObjective("scdi_sec");
            belowNameObjective = existing != null ? existing
                    : scoreboard.registerNewObjective("scdi_sec", "dummy", "s");
            belowNameObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        for (UUID id : tagged.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            CombatState state = tagged.get(id);
            if (player == null || state == null) {
                continue;
            }
            belowNameObjective.getScore(player.getName()).setScore((int) state.secondsRemaining());
        }
    }

    private void releaseBelowNameObjective() {
        if (belowNameObjective == null) {
            return;
        }
        belowNameObjective.unregister();
        belowNameObjective = null;
    }

    /** Same red -> gold -> yellow fade across the countdown the datapack
     * version used, in even thirds. Only re-evaluated once per second (this
     * runs off the same 1/sec task as the rest of the actionbar) - no point
     * chasing anything finer-grained than the display's own update rate. */
    private ChatColor phaseColor(long elapsedMs, long totalMs) {
        long pct = elapsedMs * 100 / totalMs;
        if (pct < 33) {
            return ChatColor.RED;
        } else if (pct < 66) {
            return ChatColor.GOLD;
        }
        return ChatColor.YELLOW;
    }

    private void announceTagged(Player player) {
        if (config.showTitleOnTag()) {
            player.sendTitle(ChatColor.YELLOW + "Tagged!", "", 0, 20, 10);
        }
        if (config.combatSound() != null) {
            player.playSound(player.getLocation(), config.combatSound(), config.combatVolume(), config.combatPitch());
        }
    }

    private void announceReleased(Player player) {
        sendActionBar(player, ChatColor.GREEN + "✔ Combat over - items re-enabled");
        if (config.safeSound() != null) {
            player.playSound(player.getLocation(), config.safeSound(), config.safeVolume(), config.safePitch());
        }
    }

    private void sendActionBar(Player player, String legacyText) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyText));
    }
}
