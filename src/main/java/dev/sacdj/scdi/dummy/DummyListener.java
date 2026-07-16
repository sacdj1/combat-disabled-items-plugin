package dev.sacdj.scdi.dummy;

import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** Routes any damage a dummy takes (melee, fire, fall, anything) into
 * {@link DummyManager}'s own simulated-health handling, same "however it
 * dies, not just a one-shot" scope the datapack's dummy uses. */
public final class DummyListener implements Listener {

    private final DummyManager dummies;

    public DummyListener(DummyManager dummies) {
        this.dummies = dummies;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Mannequin dummy && dummies.isDummy(dummy)) {
            dummies.handleDamage(dummy, event);
        }
    }
}
