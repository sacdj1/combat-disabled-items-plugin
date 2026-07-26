package dev.sacdj.scdi.combat;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.disguise.DisguiseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
 * <p>Sends the actionbar/title via Paper's native Adventure API
 * ({@code Player#sendActionBar}/{@code #showTitle}) rather than the old
 * Bukkit/Spigot bridge ({@code player.spigot().sendMessage(ACTION_BAR, ...)})
 * - that bridge is a legacy compatibility shim that's been quietly losing
 * functionality release over release as Paper finishes its migration to
 * Adventure, and on this build it no longer actually puts anything on
 * screen (the "In Combat" text going missing, even though the code runs
 * without error). This plugin already commits to Paper-only APIs elsewhere
 * (Mannequin, {@code ItemMeta#setItemModel}), so there's no portability
 * reason left to route through the legacy bridge here either.
 */
public final class CombatManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ScdiConfig config;
    private final DisguiseManager disguiseManager;
    private final OneShotTracker oneShotTracker;

    private final Map<UUID, CombatState> tagged = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> flashTasks = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> timerDisplays = new ConcurrentHashMap<>();
    private BukkitTask actionBarTask;
    private BukkitTask timerDisplayTask;
    private BukkitTask passiveRestoreTask;
    private long passiveRestoreTickCounter;
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
        // the floating display needs to visibly track a MOVING player, so
        // it runs faster than the actionbar - still nowhere near the
        // datapack's every-tick rate, and only doing any work at all while
        // display.show-timer-text-display is on and someone's tagged.
        timerDisplayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickTimerDisplays, 0L, 4L);
        // 1-tick resolution so combat.passive-restore-interval-ticks can be
        // changed live via /scdi reload without rescheduling anything -
        // tickPassiveRestore itself throttles the real work against that
        // value, same pattern DisguiseManager's tickFlash uses.
        passiveRestoreTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPassiveRestore, 0L, 1L);
    }

    public void stop() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        if (timerDisplayTask != null) {
            timerDisplayTask.cancel();
        }
        if (passiveRestoreTask != null) {
            passiveRestoreTask.cancel();
        }
        for (BukkitTask task : flashTasks.values()) {
            task.cancel();
        }
        flashTasks.clear();
        for (UUID displayId : timerDisplays.values()) {
            Entity display = plugin.getServer().getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        timerDisplays.clear();
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
        BukkitTask flashTask = flashTasks.remove(player.getUniqueId());
        if (flashTask != null) {
            flashTask.cancel();
        }
        UUID displayId = timerDisplays.remove(player.getUniqueId());
        if (displayId != null) {
            Entity display = plugin.getServer().getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
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
            sendActionBar(player, buildCombatText(state, elapsedMs, totalMs));
        }
    }

    /** Floating countdown that follows above a tagged player's head - a
     * real in-world {@link TextDisplay}, not the below_name scoreboard slot
     * (that one's global/shared and only shows in the player's own nametag
     * line; this one's visible to anyone standing around them). Position is
     * re-teleported every pass with a matching interpolation duration, so
     * it reads as smoothly following a moving player rather than snapping. */
    private void tickTimerDisplays() {
        if (!config.showTimerTextDisplay() || tagged.isEmpty()) {
            return;
        }
        long totalMs = Math.max(1, config.combatDuration().toMillis());
        for (UUID id : tagged.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            CombatState state = tagged.get(id);
            if (player == null || state == null) {
                continue;
            }
            TextDisplay display = timerDisplayFor(id);
            if (display == null) {
                display = spawnTimerDisplay(player);
                timerDisplays.put(id, display.getUniqueId());
            }
            long elapsedMs = totalMs - state.millisRemaining();
            boolean flashPhase = elapsedMs < 1000;
            ChatColor color = flashPhase
                    ? ((elapsedMs / 100) % 2 == 0 ? ChatColor.YELLOW : ChatColor.WHITE)
                    : phaseColor(elapsedMs, totalMs);
            display.text(LEGACY.deserialize(color + "" + ChatColor.BOLD + "⚔ " + state.secondsRemaining() + "s"));
            display.teleport(player.getLocation().add(0, 2.6, 0));
        }
    }

    private TextDisplay timerDisplayFor(UUID playerId) {
        UUID displayId = timerDisplays.get(playerId);
        if (displayId == null) {
            return null;
        }
        Entity entity = plugin.getServer().getEntity(displayId);
        return entity instanceof TextDisplay display ? display : null;
    }

    private TextDisplay spawnTimerDisplay(Player player) {
        Location loc = player.getLocation().add(0, 2.6, 0);
        return player.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(64, 0, 0, 0));
            td.setInterpolationDuration(4);
            td.setInterpolationDelay(0);
        });
    }

    /** Safety net (combat.passive-restore, default on): re-verifies anyone
     * NOT currently tagged has nothing left disguised, catching a stray
     * locked item that a bug/plugin conflict/desync left behind instead of
     * only ever cleaning up exactly once, right when combat ends normally.
     * {@link DisguiseManager#unlock} is already a cheap no-op for a player
     * with nothing tracked, so sweeping every online untagged player every
     * interval costs effectively nothing in the common case. */
    private void tickPassiveRestore() {
        if (!config.passiveRestore()) {
            return;
        }
        passiveRestoreTickCounter++;
        if (passiveRestoreTickCounter % Math.max(1, config.passiveRestoreIntervalTicks()) != 0) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isTagged(player)) {
                disguiseManager.unlock(player);
            }
        }
    }

    /** Matches the datapack's combat_active.mcfunction wording/colors:
     * red -> gold -> yellow across even thirds of the combat duration, with
     * "- items disabled" appended when display.show-disabled-text is on. */
    private String buildCombatText(CombatState state, long elapsedMs, long totalMs) {
        String suffix = config.showDisabledText() ? " - items disabled (" : " (";
        return "" + phaseColor(elapsedMs, totalMs) + "⚔ In Combat" + suffix + state.secondsRemaining() + "s)";
    }

    /** Same text as {@link #buildCombatText}, but with the datapack's
     * "(Tagged!) " flashing yellow/white prefix used for the first second
     * after a fresh tag - dark_red for the countdown itself during that
     * window, not yet phase-graded (the datapack only starts the red/gold/
     * yellow fade once a full second has elapsed too). */
    private String buildTaggedFlashText(CombatState state, boolean flashOn) {
        String suffix = config.showDisabledText() ? " - items disabled (" : " (";
        ChatColor flashColor = flashOn ? ChatColor.YELLOW : ChatColor.WHITE;
        return "" + flashColor + ChatColor.BOLD + "(Tagged!) " + ChatColor.DARK_RED
                + "⚔ In Combat" + suffix + state.secondsRemaining() + "s)";
    }

    /** Runs at 100ms resolution (2 ticks) for exactly the first second after
     * a fresh tag - matching the datapack's flash cadence - then
     * self-cancels; {@link #tickActionBars}'s normal 1/sec cadence takes
     * over seamlessly for the rest of the duration. Bounded to 10 short-lived
     * packets per tag event instead of running fine-grained the whole fight,
     * which is what the datapack itself does every tick (it's a command
     * runtime, not a scheduled task system) - no reason to pay that cost
     * continuously here just to match it visually for one second. */
    private void startTagFlash(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask previous = flashTasks.remove(id);
        if (previous != null) {
            previous.cancel();
        }
        int[] iteration = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            CombatState state = tagged.get(id);
            if (state == null || !player.isOnline() || iteration[0] >= 10) {
                holder[0].cancel();
                flashTasks.remove(id);
                return;
            }
            sendActionBar(player, buildTaggedFlashText(state, iteration[0] % 2 == 0));
            iteration[0]++;
        }, 0L, 2L);
        flashTasks.put(id, holder[0]);
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
            // matches the datapack's on_hurt_by_player_first.mcfunction
            // exactly: "title @s times 0 20 5" (fadeIn 0, stay 20 ticks,
            // fadeOut 5 ticks) with this title/subtitle text.
            player.showTitle(Title.title(
                    LEGACY.deserialize(ChatColor.RED + "" + ChatColor.BOLD + "⚔ In Combat!"),
                    LEGACY.deserialize(ChatColor.GRAY + "Items disabled for " + config.combatDuration().toSeconds() + "s"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(250))));
        }
        if (config.combatSound() != null) {
            player.playSound(player.getLocation(), config.combatSound(), config.combatVolume(), config.combatPitch());
        }
        if (config.showActionBar()) {
            startTagFlash(player);
        }
    }

    private void announceReleased(Player player) {
        sendActionBar(player, ChatColor.GREEN + "✔ Combat over - items re-enabled");
        if (config.safeSound() != null) {
            player.playSound(player.getLocation(), config.safeSound(), config.safeVolume(), config.safePitch());
        }
    }

    private void sendActionBar(Player player, String legacyText) {
        player.sendActionBar(LEGACY.deserialize(legacyText));
    }
}
