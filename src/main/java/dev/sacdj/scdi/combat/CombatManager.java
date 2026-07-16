package dev.sacdj.scdi.combat;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.disguise.DisguiseManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

    private final Map<UUID, CombatState> tagged = new ConcurrentHashMap<>();
    private BukkitTask actionBarTask;

    public CombatManager(JavaPlugin plugin, ScdiConfig config, DisguiseManager disguiseManager) {
        this.plugin = plugin;
        this.config = config;
        this.disguiseManager = disguiseManager;
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

    public void release(Player player) {
        CombatState state = tagged.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.cancel();
        disguiseManager.unlock(player);
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
            player.playSound(player.getLocation(), config.combatSound(), 1.0f, config.combatPitch());
        }
    }

    private void announceReleased(Player player) {
        sendActionBar(player, ChatColor.GREEN + "✔ Combat over - items re-enabled");
        if (config.safeSound() != null) {
            player.playSound(player.getLocation(), config.safeSound(), 1.0f, config.safePitch());
        }
    }

    private void sendActionBar(Player player, String legacyText) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyText));
    }
}
