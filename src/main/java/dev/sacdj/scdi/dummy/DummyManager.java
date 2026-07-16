package dev.sacdj.scdi.dummy;

import dev.sacdj.scdi.config.ScdiConfig;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-dummy system: a static {@link Mannequin} target for testing without a
 * second real player. Paper-only (Mannequin's Bukkit API surface pulls in
 * Paper-specific types like {@code Immovable}/{@code SkinParts}), unlike the
 * rest of this plugin which sticks to classic Bukkit/Spigot APIs.
 *
 * <p>Real {@link Mannequin#setHealth} is kept as a large safety-buffer pool
 * (mirrors the datapack's dummy_max_health) - the actual death/one-shot gate
 * is a separate simulated pool ({@link DummyState#simHp}), sized to
 * {@code dummy.one-shot-damage} (a normal player's health), so sustained
 * damage kills a dummy the same way it would a real player, not just a
 * single huge hit.
 */
public final class DummyManager {

    private final JavaPlugin plugin;
    private final ScdiConfig config;
    private final NamespacedKey ownerKey;

    private final Map<UUID, Long> spawnCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, DummyState> dummies = new ConcurrentHashMap<>();

    private BukkitTask regenTask;
    private BukkitTask lookTask;
    private BukkitTask pickupTask;
    private long regenTickCounter;
    private long lookTickCounter;
    private final Map<UUID, Long> pickupCooldownUntil = new ConcurrentHashMap<>();

    public DummyManager(JavaPlugin plugin, ScdiConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.ownerKey = new NamespacedKey(plugin, "dummy_owner");
    }

    public void start() {
        regenTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRegen, 0L, 1L);
        lookTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickLook, 0L, 1L);
        pickupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPickup, 0L, 10L);
    }

    public void stop() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        if (lookTask != null) {
            lookTask.cancel();
        }
        if (pickupTask != null) {
            pickupTask.cancel();
        }
    }

    public boolean isDummy(Entity entity) {
        return dummies.containsKey(entity.getUniqueId());
    }

    /** @return an error message, or null on success. */
    public String spawn(Player requester) {
        long now = System.currentTimeMillis();
        Long cooldownUntil = spawnCooldownUntil.get(requester.getUniqueId());
        if (cooldownUntil != null && now < cooldownUntil) {
            long remainSeconds = (cooldownUntil - now + 999) / 1000;
            return "Dummy spawn is on cooldown - " + remainSeconds + "s left.";
        }

        long perPlayerCount = dummies.values().stream()
                .filter(state -> state.ownerId.equals(requester.getUniqueId())).count();
        if (perPlayerCount >= config.dummyMaxPerPlayer()) {
            return "You already have " + config.dummyMaxPerPlayer() + " dummies out (your limit).";
        }
        if (dummies.size() >= config.dummyMaxTotal()) {
            return "The server-wide dummy limit (" + config.dummyMaxTotal() + ") has been reached.";
        }

        Location loc = requester.getLocation().clone();
        loc.add(loc.getDirection().normalize().multiply(2));
        loc.setY(requester.getLocation().getY());

        Mannequin dummy = requester.getWorld().spawn(loc, Mannequin.class, m -> {
            m.setImmovable(config.dummyImmobile());
            AttributeInstance maxHealth = m.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(config.dummyMaxHealth());
            }
            m.setHealth(config.dummyMaxHealth());
        });
        dummy.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, requester.getUniqueId().toString());

        DummyState state = new DummyState(requester.getUniqueId());
        state.simHp = config.dummyOneShotDamage();
        state.invincible = config.dummyInvincibleDefault();
        state.lastHitMillis = now;
        dummies.put(dummy.getUniqueId(), state);

        spawnCooldownUntil.put(requester.getUniqueId(), now + config.dummySpawnCooldownSeconds() * 1000);

        if (config.dummyShowHealth()) {
            spawnHealthDisplay(dummy, state);
        }
        return null;
    }

    public void remove(Mannequin dummy) {
        DummyState state = dummies.remove(dummy.getUniqueId());
        if (state != null && state.healthDisplayId != null) {
            Entity display = plugin.getServer().getEntity(state.healthDisplayId);
            if (display != null) {
                display.remove();
            }
        }
        dummy.remove();
    }

    public int removeAll() {
        int count = 0;
        for (UUID id : dummies.keySet()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity instanceof Mannequin mannequin) {
                remove(mannequin);
                count++;
            }
        }
        return count;
    }

    public void setInvincible(Mannequin dummy, boolean invincible) {
        DummyState state = dummies.get(dummy.getUniqueId());
        if (state != null) {
            state.invincible = invincible;
        }
    }

    public void handleDamage(Mannequin dummy, EntityDamageEvent event) {
        DummyState state = dummies.get(dummy.getUniqueId());
        if (state == null) {
            return;
        }
        event.setCancelled(true);

        double damage = event.getFinalDamage();
        if (damage <= 0) {
            return;
        }
        boolean firstHit = state.hitThisEncounter.compareAndSet(false, true);
        if (firstHit) {
            state.encounterStartMillis = System.currentTimeMillis();
        }
        if (config.dummyDamageNumbers()) {
            spawnDamagePopup(dummy, damage);
        }
        state.lastHitMillis = System.currentTimeMillis();
        state.simHp -= damage;
        dummy.setHealth(Math.max(1.0, dummy.getHealth() - damage));

        if (state.simHp <= 0) {
            if (state.invincible) {
                cheatDeath(dummy, state);
            } else {
                killDummy(dummy, state, firstHit);
                return;
            }
        }
        updateHealthDisplay(dummy, state);
    }

    private void cheatDeath(Mannequin dummy, DummyState state) {
        AttributeInstance maxHealth = dummy.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : config.dummyMaxHealth();
        dummy.setHealth(max);
        state.simHp = config.dummyOneShotDamage();
        dummy.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dummy.getLocation().add(0, 1, 0), 25);
        dummy.getWorld().playSound(dummy.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    private void killDummy(Mannequin dummy, DummyState state, boolean wasOneShot) {
        EntityEquipment equipment = dummy.getEquipment();
        if (equipment != null) {
            for (ItemStack item : equipment.getArmorContents()) {
                dropItem(dummy, item);
            }
            dropItem(dummy, equipment.getItemInMainHand());
            dropItem(dummy, equipment.getItemInOffHand());
        }

        if (wasOneShot && config.dummyAnnounceOneShot()) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "ONE SHOT" + ChatColor.RESET
                    + ChatColor.GRAY + " - a test dummy went down in a single hit!");
        }
        if (config.dummyAnnounceTimeToKill() && state.encounterStartMillis > 0) {
            double seconds = (System.currentTimeMillis() - state.encounterStartMillis) / 1000.0;
            Bukkit.broadcastMessage(ChatColor.GRAY + String.format("Dummy killed in %.2fs", seconds));
        }

        remove(dummy);
    }

    /** Brief RPG-style "-N" popup on every hit - a one-off TextDisplay,
     * randomly offset so a fast flurry of hits doesn't stack them all on
     * top of each other unreadably, removed a second later via a delayed
     * task rather than tracked/updated like the health display. */
    private void spawnDamagePopup(Mannequin dummy, double damage) {
        double offsetX = (Math.random() - 0.5) * 0.6;
        double offsetZ = (Math.random() - 0.5) * 0.6;
        Location loc = dummy.getLocation().add(offsetX, 2.0, offsetZ);
        TextDisplay popup = dummy.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setText(ChatColor.RED + String.format("-%.1f", damage));
            td.setSeeThrough(false);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, popup::remove, 20L);
    }

    private void dropItem(Mannequin dummy, ItemStack item) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            dummy.getWorld().dropItemNaturally(dummy.getLocation(), item);
        }
    }

    private void spawnHealthDisplay(Mannequin dummy, DummyState state) {
        TextDisplay display = dummy.getWorld().spawn(dummy.getLocation().add(0, 2.4, 0), TextDisplay.class, td -> {
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setSeeThrough(false);
        });
        state.healthDisplayId = display.getUniqueId();
        dummy.addPassenger(display);
        updateHealthDisplay(dummy, state);
    }

    private void updateHealthDisplay(Mannequin dummy, DummyState state) {
        if (state.healthDisplayId == null) {
            return;
        }
        Entity displayEntity = plugin.getServer().getEntity(state.healthDisplayId);
        if (!(displayEntity instanceof TextDisplay display)) {
            return;
        }
        AttributeInstance maxHealth = dummy.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : config.dummyMaxHealth();
        display.setText(ChatColor.WHITE + String.format("%.0f / %.0f", dummy.getHealth(), max));
    }

    private void tickRegen() {
        if (dummies.isEmpty()) {
            return;
        }
        regenTickCounter++;
        if (regenTickCounter % config.dummyRegenIntervalTicks() != 0) {
            return;
        }
        if (!config.dummyRegenEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long delayMillis = config.dummyRegenDelaySeconds() * 1000;
        for (Iterator<Map.Entry<UUID, DummyState>> it = dummies.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, DummyState> entry = it.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof Mannequin dummy)) {
                it.remove();
                continue;
            }
            DummyState state = entry.getValue();
            if (now - state.lastHitMillis < delayMillis) {
                continue;
            }
            AttributeInstance maxHealth = dummy.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealth != null ? maxHealth.getValue() : config.dummyMaxHealth();
            if (dummy.getHealth() < max) {
                dummy.setHealth(Math.min(max, dummy.getHealth() + config.dummyRegenAmount()));
            }
            if (state.simHp < config.dummyOneShotDamage()) {
                state.simHp = Math.min(config.dummyOneShotDamage(), state.simHp + config.dummyRegenAmount());
            }
            if (dummy.getHealth() >= max) {
                state.hitThisEncounter.set(false);
            }
            updateHealthDisplay(dummy, state);
        }
    }

    /** Runs at a fixed 10-tick (2/sec) rate rather than every tick - a
     * nearby-entity scan per dummy is the expensive part here, and pickup
     * timing doesn't need to be tighter than that to look responsive. */
    private void tickPickup() {
        if (dummies.isEmpty() || !config.dummyPickupItems()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID id : dummies.keySet()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Mannequin dummy)) {
                continue;
            }
            Long cooldownUntil = pickupCooldownUntil.get(id);
            if (cooldownUntil != null && now < cooldownUntil) {
                continue;
            }
            for (Entity nearby : dummy.getNearbyEntities(1.5, 1.5, 1.5)) {
                if (nearby instanceof Item item && tryEquip(dummy, item.getItemStack())) {
                    item.remove();
                    pickupCooldownUntil.put(id, now + 1000);
                    break;
                }
            }
        }
    }

    private boolean tryEquip(Mannequin dummy, ItemStack stack) {
        EquipmentSlot slot = armorSlotFor(stack.getType());
        if (slot == null) {
            return false;
        }
        EntityEquipment equipment = dummy.getEquipment();
        if (equipment == null) {
            return false;
        }
        ItemStack existing = equipment.getItem(slot);
        if (existing != null && existing.getType() != org.bukkit.Material.AIR) {
            dropItem(dummy, existing);
        }
        equipment.setItem(slot, stack);
        return true;
    }

    private EquipmentSlot armorSlotFor(org.bukkit.Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return EquipmentSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    private void tickLook() {
        if (dummies.isEmpty() || !config.dummyLookAtPlayer()) {
            return;
        }
        lookTickCounter++;
        if (lookTickCounter % 2 != 0) {
            return;
        }
        for (UUID id : dummies.keySet()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Mannequin dummy)) {
                continue;
            }
            LivingEntity nearest = null;
            double nearestDistSq = config.dummyLookRange() * config.dummyLookRange();
            for (Entity nearby : dummy.getNearbyEntities(config.dummyLookRange(), config.dummyLookRange(), config.dummyLookRange())) {
                if (nearby instanceof Player player) {
                    double distSq = player.getLocation().distanceSquared(dummy.getLocation());
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = player;
                    }
                }
            }
            if (nearest != null) {
                Location look = dummy.getLocation().clone();
                Location target = nearest.getEyeLocation();
                Location from = dummy.getEyeLocation();
                double dx = target.getX() - from.getX();
                double dy = target.getY() - from.getY();
                double dz = target.getZ() - from.getZ();
                double distanceXZ = Math.sqrt(dx * dx + dz * dz);
                look.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                look.setPitch((float) Math.toDegrees(-Math.atan2(dy, distanceXZ)));
                dummy.teleport(look);
            }
        }
    }

    private static final class DummyState {
        final UUID ownerId;
        double simHp;
        boolean invincible;
        long lastHitMillis;
        long encounterStartMillis;
        final java.util.concurrent.atomic.AtomicBoolean hitThisEncounter = new java.util.concurrent.atomic.AtomicBoolean(false);
        UUID healthDisplayId;

        DummyState(UUID ownerId) {
            this.ownerId = ownerId;
        }
    }
}
