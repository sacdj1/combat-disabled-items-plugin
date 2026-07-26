package dev.sacdj.scdi.debug;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Testing-only admin utility, not part of the shipped SCDI feature set -
 * lets whoever's testing disappear from other players' tab list/visibility
 * (and stop colliding with them) so live testing isn't constantly
 * interrupted by other people on the server. Reachable only via
 * /scdi debug vanish (admin-gated, same as the rest of /scdi debug). */
public final class VanishManager {

    private final JavaPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    /** @return true if the player is now vanished, false if un-vanished. */
    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.showPlayer(plugin, player);
            }
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.setCollidable(true);
            return false;
        }
        vanished.add(player.getUniqueId());
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(plugin, player);
            }
        }
        // infinite, no ambient particles/icon - a real potion effect (not
        // just hidePlayer) so mobs don't aggro/target while vanished either.
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 0, false, false, false));
        player.setCollidable(false);
        return true;
    }

    /** Called on join so anyone already vanished stays hidden from a
     * newly-connecting player too, instead of only from whoever was online
     * at the moment vanish was toggled. */
    public void onJoin(Player joined) {
        if (vanished.isEmpty()) {
            return;
        }
        for (UUID id : vanished) {
            Player vanishedPlayer = plugin.getServer().getPlayer(id);
            if (vanishedPlayer != null && !vanishedPlayer.equals(joined)) {
                joined.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    /** Called on quit to drop bookkeeping for a vanished player who
     * disconnects, instead of leaving a stale entry that'd otherwise try
     * (harmlessly, since getPlayer() would just return null) to re-hide a
     * long-gone player from every future joiner forever. */
    public void onQuit(Player player) {
        vanished.remove(player.getUniqueId());
    }
}
