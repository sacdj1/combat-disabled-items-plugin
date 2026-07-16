package dev.sacdj.scdi.combat;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.team.TeamManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Translates real damage events into tag/release calls. MONITOR + ignoreCancelled
 * so this only reacts to damage that actually landed - mirrors the datapack's
 * advancement-based detection, which only fired on genuine applied damage,
 * not damage another plugin/vanilla rule blocked.
 *
 * <p>Bukkit fires {@link EntityDamageByEntityEvent} BEFORE subtracting the
 * damage from the victim's health, even at MONITOR priority - post-damage
 * health has to be computed manually ({@code getHealth() - getFinalDamage()})
 * rather than read directly, which is what the one-shot check below does.
 */
public final class CombatListener implements Listener {

    private final ScdiConfig config;
    private final CombatManager combat;
    private final OneShotTracker oneShot;
    private final TeamManager teams;

    public CombatListener(ScdiConfig config, CombatManager combat, OneShotTracker oneShot, TeamManager teams) {
        this.config = config;
        this.combat = combat;
        this.oneShot = oneShot;
        this.teams = teams;
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

            boolean victimWasTagged = combat.isTagged(victim);
            double postDamageHealth = victim.getHealth() - event.getFinalDamage();
            boolean wasOneShot = oneShot.onPlayerHit(victim, victimWasTagged, postDamageHealth);

            if (!config.hitTaggingEnabled()) {
                return;
            }
            boolean skipVictim = wasOneShot && config.oneShotNoTagVictim();
            boolean skipAttacker = wasOneShot && config.oneShotNoTagAttacker();
            boolean sameTeam = teams.sameTeam(victim, attacker);
            boolean victimGate = sameTeam ? config.teamTagVictim() : config.tagVictim();
            boolean attackerGate = sameTeam ? config.teamTagAttacker() : config.tagAttacker();

            if (victimGate && !skipVictim) {
                maybeTag(victim);
            }
            if (attackerGate && !skipAttacker) {
                maybeTag(attacker);
            }
            return;
        }

        if (!config.hitTaggingEnabled() || !config.pveMode()) {
            return;
        }

        if (victimIsPlayer) {
            maybeTag((Player) victimEntity);
        } else if (attackerIsPlayer) {
            maybeTag((Player) attackerEntity);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        oneShot.onDeath(player);
        if (config.resetOnDeath() && combat.isTagged(player)) {
            combat.release(player);
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
