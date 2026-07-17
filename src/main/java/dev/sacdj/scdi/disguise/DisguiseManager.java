package dev.sacdj.scdi.disguise;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.warning.WarningManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swaps real items for a disguise item and back. Unlike the datapack's
 * approach (rebuild an item from a captured NBT component snapshot via
 * commands - the source of the equippable-negation bug and its relatives),
 * this holds the actual original {@link ItemStack} object in memory and puts
 * the same object back on unlock. There's no serialization round-trip to lose
 * data in, so that whole bug class is structurally impossible here.
 *
 * <p>Each disguise item carries its own unique instance id (a random UUID
 * baked into its persistent data, separate from the plain boolean "this is
 * a disguise" marker), and restoring on unlock searches the player's WHOLE
 * inventory for whichever slot currently holds that id, rather than
 * remembering a fixed slot from lock-time. That's deliberate: a player has
 * to be free to reorganize their own inventory (hotbar order, which armor
 * slot, hand swap) while tagged without it breaking restoration or opening
 * a duplication hole. {@link DisguiseProtectionListener} still blocks the
 * two things that actually matter - leaving the inventory entirely (drop,
 * placing as a block) or moving into a DIFFERENT inventory (a chest,
 * another player, a villager trade, ...).
 */
public final class DisguiseManager {

    private static final Map<EquipmentSlot, String> ARMOR_SUFFIX = new EnumMap<>(Map.of(
            EquipmentSlot.HEAD, "_HELMET",
            EquipmentSlot.CHEST, "_CHESTPLATE",
            EquipmentSlot.LEGS, "_LEGGINGS",
            EquipmentSlot.FEET, "_BOOTS"
    ));

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey instanceKey;
    private final ScdiConfig config;
    private final WarningManager warnings;

    private final Map<UUID, List<LockedItem>> locked = new ConcurrentHashMap<>();
    private BukkitTask flashTask;
    private BukkitTask refreshTask;
    private long flashTickCounter;

    public DisguiseManager(JavaPlugin plugin, ScdiConfig config, WarningManager warnings) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "disguised");
        this.instanceKey = new NamespacedKey(plugin, "disguise_instance");
        this.config = config;
        this.warnings = warnings;
    }

    public void start() {
        flashTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFlash, 0L, 1L);
        // catches anything picked up/equipped AFTER the initial tag that
        // wasn't there to scan yet (e.g. unequip a rocket right before
        // getting hit, re-equip mid-fight) - without this, lock() only ever
        // ran once per tag/retag, leaving a real bypass for anyone who
        // timed their inventory around the hit. Every 10 ticks (2/sec) is
        // plenty responsive without scanning tagged players' inventories
        // every tick.
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRefresh, 0L, 10L);
    }

    public void stop() {
        if (flashTask != null) {
            flashTask.cancel();
        }
        if (refreshTask != null) {
            refreshTask.cancel();
        }
    }

    private void tickRefresh() {
        if (locked.isEmpty()) {
            return;
        }
        for (UUID id : locked.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                lock(player);
            }
        }
    }

    public void lock(Player player) {
        locked.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        List<LockedItem> items = locked.get(player.getUniqueId());
        PlayerInventory inv = player.getInventory();

        lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.HAND));
        lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.OFF_HAND));

        if (config.disableElytra()) {
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.CHEST));
        }
        if (config.disableArmor()) {
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.HEAD));
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.CHEST));
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.LEGS));
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.FEET));
        }

        if (config.scanFullInventory()) {
            for (int i = 0; i < 36; i++) {
                lockIfMatch(player, items, inv, new ItemLocation.InventorySlot(i));
            }
        }
    }

    public void unlock(Player player) {
        warnings.onCombatEnd(player);
        List<LockedItem> items = locked.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (LockedItem item : items) {
            if (!restoreByInstanceId(player, inv, item)) {
                // genuinely couldn't find the disguise item anywhere in
                // their inventory (shouldn't happen with drop/move/place
                // protection in place, but never silently lose the real
                // item over it either) - hand the original back wherever
                // it fits, or drop it at their feet if their inventory's
                // completely full.
                Map<Integer, ItemStack> overflow = inv.addItem(item.original());
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }

    /** Searches every slot the item could currently be sitting in (it's
     * free to move within the player's own inventory while locked - see
     * the class javadoc) for the matching instance id, and replaces it
     * with the original wherever it's actually found. */
    private boolean restoreByInstanceId(Player player, PlayerInventory inv, LockedItem locked) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack current = inv.getItem(slot);
            if (matchesInstance(current, locked.instanceId())) {
                inv.setItem(slot, locked.original());
                return true;
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack current = inv.getItem(i);
            if (matchesInstance(current, locked.instanceId())) {
                inv.setItem(i, locked.original());
                return true;
            }
        }
        return false;
    }

    private boolean matchesInstance(ItemStack item, String instanceId) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        return instanceId.equals(id);
    }

    /** Re-checks a still-tagged player's inventory for anything that should
     * be locked but isn't yet (e.g. picked up mid-fight, or swapped into a
     * slot scanFullInventory watches) - a lighter version of {@link #lock},
     * safe to call repeatedly on an already-locked player. */
    public void refresh(Player player) {
        if (!locked.containsKey(player.getUniqueId())) {
            return;
        }
        lock(player);
    }

    private void tickFlash() {
        if (!config.disguiseArmorFlash() || locked.isEmpty()) {
            return;
        }
        flashTickCounter++;
        if (flashTickCounter % config.disguiseArmorFlashIntervalTicks() != 0) {
            return;
        }
        for (UUID id : locked.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (hasDisguisedArmor(player)) {
                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
            }
        }
    }

    /** Checked live against the player's CURRENT equipment, not stored
     * lock-time metadata - since a disguised item is free to move between
     * armor slots (or out of them) while locked, only the live state
     * reliably answers "is anything they're wearing right now disguised". */
    private boolean hasDisguisedArmor(Player player) {
        var equipment = player.getInventory();
        return isDisguised(equipment.getHelmet()) || isDisguised(equipment.getChestplate())
                || isDisguised(equipment.getLeggings()) || isDisguised(equipment.getBoots());
    }

    private void lockIfMatch(Player player, List<LockedItem> items, PlayerInventory inv, ItemLocation loc) {
        ItemStack current = loc.read(inv);
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        if (isDisguised(current)) {
            return;
        }
        if (!matchesDisabledItem(current, loc)) {
            return;
        }
        String instanceId = UUID.randomUUID().toString();
        items.add(new LockedItem(instanceId, current.clone()));
        loc.write(inv, buildDisguiseItem(loc, current.getAmount(), instanceId));

        if (loc instanceof ItemLocation.Equipment eq && ARMOR_SUFFIX.containsKey(eq.slot())) {
            warnings.maybeWarnArmor(player);
        } else {
            warnings.maybeWarnInventory(player);
        }
    }

    private boolean matchesDisabledItem(ItemStack item, ItemLocation loc) {
        Material type = item.getType();
        if (loc instanceof ItemLocation.Equipment eq) {
            if (eq.slot() == EquipmentSlot.CHEST && type == Material.ELYTRA) {
                return config.disableElytra();
            }
            if (ARMOR_SUFFIX.containsKey(eq.slot()) && config.disableArmor()) {
                return true;
            }
        }
        if (type == Material.FIREWORK_ROCKET) {
            return config.disableFireworkRocket();
        }
        if (type == Material.WIND_CHARGE) {
            return config.disableWindCharge();
        }
        Set<Material> custom = config.customDisabledItems();
        return custom.contains(type);
    }

    private ItemStack buildDisguiseItem(ItemLocation loc, int amount, String instanceId) {
        if (loc instanceof ItemLocation.Equipment eq && ARMOR_SUFFIX.containsKey(eq.slot())) {
            return buildDisguiseArmorItem(eq.slot(), amount, instanceId);
        }
        Material material = resolveMaterial(config.disguiseItemKey(), Material.STICK);
        ItemStack disguise = new ItemStack(material, amount);
        ItemMeta meta = disguise.getItemMeta();
        meta.setDisplayName(ChatColor.RED + config.disguiseDisplayName());
        meta.setEnchantmentGlintOverride(config.disguiseGlint());
        // purely visual (minecraft:item_model) - the item's real type above
        // is what disable it functionally works against, this just changes
        // what it looks like held/in a slot. Paper exposes this directly;
        // the datapack had to fake it with a component-patch relay trick.
        NamespacedKey modelKey = config.disguiseModelKey();
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId);
        disguise.setItemMeta(meta);
        return disguise;
    }

    /** A worn disguise needs to actually BE an armor piece of the right slot
     * to render at all - an arbitrary item (a stick) shows as nothing on the
     * player's body when placed directly in an armor slot. Falls back to a
     * plain stick if the configured material name doesn't resolve to a real
     * armor piece for this slot. Also applies disguise.model on top, for the
     * inventory icon - the WORN appearance is controlled by the real armor
     * material chosen above, item_model doesn't affect that. */
    private ItemStack buildDisguiseArmorItem(EquipmentSlot slot, int amount, String instanceId) {
        String materialName = config.disguiseArmorMaterial().toUpperCase() + ARMOR_SUFFIX.get(slot);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.matchMaterial("LEATHER" + ARMOR_SUFFIX.get(slot));
        }
        ItemStack disguise = new ItemStack(material != null ? material : Material.STICK, 1);
        ItemMeta meta = disguise.getItemMeta();
        meta.setDisplayName(ChatColor.RED + config.disguiseDisplayName());
        meta.setEnchantmentGlintOverride(config.disguiseGlint());
        NamespacedKey modelKey = config.disguiseModelKey();
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId);
        disguise.setItemMeta(meta);
        return disguise;
    }

    private Material resolveMaterial(NamespacedKey key, Material fallback) {
        if (key == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(key.getKey());
        return material != null ? material : fallback;
    }

    /** Public - used by {@link DisguiseProtectionListener} to block a
     * disguise item leaving the player's own inventory (drop, place as a
     * block) or moving into a different inventory (chest, trade, another
     * player, ...) - both would separate it from the tracking that lets
     * {@link #unlock} find it again, which is how the item duplication bug
     * reported this session actually worked. Free movement WITHIN a
     * player's own inventory is deliberately allowed - see the class
     * javadoc. */
    public boolean isDisguised(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    private record LockedItem(String instanceId, ItemStack original) {
    }
}
