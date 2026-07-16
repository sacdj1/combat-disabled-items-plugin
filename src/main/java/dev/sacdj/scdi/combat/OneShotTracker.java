package dev.sacdj.scdi.combat;

import dev.sacdj.scdi.config.ScdiConfig;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "One-shot" = a player's first hit of a fresh encounter (not already tagged
 * going in) is also the killing blow. Mirrors the datapack's
 * scdi_one_shot_hit gate: {@link #freshEncounter} latches false the instant
 * ANY hit lands (lethal or not), and only resets at combat-end/death - a
 * two-hit kill was never a one-shot, even if the gate looks "fresh" again by
 * the time the second hit lands.
 */
public final class OneShotTracker {

    private final ScdiConfig config;

    private final Map<UUID, Boolean> freshEncounter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatEndMillis = new ConcurrentHashMap<>();

    public OneShotTracker(ScdiConfig config) {
        this.config = config;
    }

    /**
     * Called for every player-vs-player hit, victim's side.
     *
     * @param victimWasTagged whether the victim was already tagged going
     *                        INTO this specific hit (before this hit's own
     *                        tagging is applied)
     * @param postDamageHealth victim's health after this hit's damage is
     *                         subtracted, computed by the caller since the
     *                         event fires before Bukkit applies it
     * @return true if this hit was announced as a one-shot
     */
    public boolean onPlayerHit(Player victim, boolean victimWasTagged, double postDamageHealth) {
        boolean ignoreTag = config.oneShotIgnoreTag();
        if (!ignoreTag && victimWasTagged) {
            return false;
        }

        boolean isFreshHit = freshEncounter.putIfAbsent(victim.getUniqueId(), Boolean.FALSE) == null;
        if (!isFreshHit) {
            return false;
        }

        if (postDamageHealth > 0) {
            return false;
        }
        if (!config.oneShotAnnounce()) {
            return false;
        }
        if (config.oneShotCooldownEnabled()) {
            Long lastEnd = lastCombatEndMillis.get(victim.getUniqueId());
            if (lastEnd != null) {
                long elapsedSeconds = (System.currentTimeMillis() - lastEnd) / 1000;
                if (elapsedSeconds < config.oneShotCooldownSeconds()) {
                    return false;
                }
            }
        }

        announce(victim);
        return true;
    }

    /** Fresh-encounter boundary, same as the datapack's combat_end.mcfunction
     * reset - lets the next encounter's first hit count again. */
    public void onCombatEnd(Player player) {
        freshEncounter.remove(player.getUniqueId());
        lastCombatEndMillis.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** A real death is always a fresh-encounter boundary, independent of
     * combat_end (e.g. reset-on-death off but the player still died). */
    public void onDeath(Player player) {
        freshEncounter.remove(player.getUniqueId());
    }

    private void announce(Player victim) {
        Bukkit.broadcastMessage(
                ChatColor.RED + "⚔ " + ChatColor.RESET + victim.getName()
                        + ChatColor.GRAY + " was " + ChatColor.RED + "" + ChatColor.BOLD + "ONE-SHOT" + ChatColor.RESET + ChatColor.GRAY + "!");
    }
}
