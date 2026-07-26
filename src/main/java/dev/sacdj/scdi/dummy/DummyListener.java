package dev.sacdj.scdi.dummy;

import dev.sacdj.scdi.combat.CombatManager;
import dev.sacdj.scdi.config.ScdiConfig;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Routes any damage a dummy takes (melee, fire, fall, anything) into
 * {@link DummyManager}'s own simulated-health handling, same "however it
 * dies, not just a one-shot" scope the datapack's dummy uses.
 *
 * <p>Also handles dummy.tagging (hitting a dummy tags the attacker, same as
 * hitting a real player would - lets the whole tag flow be tested solo)
 * directly here rather than in {@link dev.sacdj.scdi.combat.CombatListener}:
 * {@link DummyManager#handleDamage} always cancels the event, and
 * CombatListener's handlers are ignoreCancelled - by the time they'd see it,
 * it's too late. */
public final class DummyListener implements Listener {

    private final DummyManager dummies;
    private final CombatManager combat;
    private final ScdiConfig config;

    public DummyListener(DummyManager dummies, CombatManager combat, ScdiConfig config) {
        this.dummies = dummies;
        this.combat = combat;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin dummy) || !dummies.isDummy(dummy)) {
            return;
        }
        if (config.dummyTagging() && config.hitTaggingEnabled() && event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = resolveAttacker(byEntity.getDamager());
            if (attacker != null && !(config.ignoreCreative() && attacker.getGameMode() == GameMode.CREATIVE)) {
                combat.tag(attacker);
            }
        }
        dummies.handleDamage(dummy, event);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
