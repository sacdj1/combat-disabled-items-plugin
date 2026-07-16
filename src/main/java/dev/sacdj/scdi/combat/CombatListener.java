package dev.sacdj.scdi.combat;

import dev.sacdj.scdi.config.ScdiConfig;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Translates real damage events into tag/release calls. MONITOR + ignoreCancelled
 * so this only reacts to damage that actually landed - mirrors the datapack's
 * advancement-based detection, which only fired on genuine applied damage,
 * not damage another plugin/vanilla rule blocked.
 */
public final class CombatListener implements Listener {

    private final ScdiConfig config;
    private final CombatManager combat;

    public CombatListener(ScdiConfig config, CombatManager combat) {
        this.config = config;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victimEntity = event.getEntity();
        Entity rawDamager = event.getDamager();
        Entity attackerEntity = rawDamager instanceof Projectile projectile
                ? asEntity(projectile.getShooter())
                : rawDamager;

        boolean isRanged = rawDamager instanceof Projectile;
        if (isRanged && !config.rangedAttacksTag()) {
            return;
        }

        boolean victimIsPlayer = victimEntity instanceof Player;
        boolean attackerIsPlayer = attackerEntity instanceof Player;

        if (victimIsPlayer && attackerIsPlayer) {
            Player victim = (Player) victimEntity;
            Player attacker = (Player) attackerEntity;
            if (config.tagVictim()) {
                maybeTag(victim);
            }
            if (config.tagAttacker()) {
                maybeTag(attacker);
            }
            return;
        }

        if (!config.pveMode()) {
            return;
        }

        if (victimIsPlayer) {
            maybeTag((Player) victimEntity);
        } else if (attackerIsPlayer) {
            maybeTag((Player) attackerEntity);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.release(event.getPlayer());
    }

    private void maybeTag(Player player) {
        if (config.ignoreCreative() && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        combat.tag(player);
    }

    private static Entity asEntity(org.bukkit.projectiles.ProjectileSource source) {
        return source instanceof Entity entity ? entity : null;
    }
}
