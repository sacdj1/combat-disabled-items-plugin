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
    private final ScdiConfig config;
    private final WarningManager warnings;

    private final Map<UUID, List<LockedItem>> locked = new ConcurrentHashMap<>();
    private BukkitTask flashTask;
    private long flashTickCounter;

    public DisguiseManager(JavaPlugin plugin, ScdiConfig config, WarningManager warnings) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "disguised");
        this.config = config;
        this.warnings = warnings;
    }

    public void start() {
        flashTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFlash, 0L, 1L);
    }

    public void stop() {
        if (flashTask != null) {
            flashTask.cancel();
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
            item.location().write(inv, item.original());
        }
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

    public boolean isDisguisedArmorSlot(Player player, EquipmentSlot slot) {
        List<LockedItem> items = locked.get(player.getUniqueId());
        if (items == null) {
            return false;
        }
        for (LockedItem item : items) {
            if (item.location() instanceof ItemLocation.Equipment eq && eq.slot() == slot) {
                return true;
            }
        }
        return false;
    }

    private void tickFlash() {
        if (!config.disguiseArmorFlash() || locked.isEmpty()) {
            return;
        }
        flashTickCounter++;
        if (flashTickCounter % config.disguiseArmorFlashIntervalTicks() != 0) {
            return;
        }
        for (Map.Entry<UUID, List<LockedItem>> entry : locked.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean hasArmor = false;
            for (LockedItem item : entry.getValue()) {
                if (item.location() instanceof ItemLocation.Equipment eq && ARMOR_SUFFIX.containsKey(eq.slot())) {
                    hasArmor = true;
                    break;
                }
            }
            if (hasArmor) {
                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
            }
        }
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
        items.add(new LockedItem(loc, current.clone()));
        loc.write(inv, buildDisguiseItem(loc, current.getAmount()));

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

    private ItemStack buildDisguiseItem(ItemLocation loc, int amount) {
        if (loc instanceof ItemLocation.Equipment eq && ARMOR_SUFFIX.containsKey(eq.slot())) {
            return buildDisguiseArmorItem(eq.slot(), amount);
        }
        ItemStack disguise = new ItemStack(Material.STICK, amount);
        ItemMeta meta = disguise.getItemMeta();
        meta.setDisplayName(ChatColor.RED + config.disguiseDisplayName());
        meta.setEnchantmentGlintOverride(config.disguiseGlint());
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        disguise.setItemMeta(meta);
        return disguise;
    }

    /** A worn disguise needs to actually BE an armor piece of the right slot
     * to render at all - an arbitrary item (a stick) shows as nothing on the
     * player's body when placed directly in an armor slot. Falls back to a
     * plain stick if the configured material name doesn't resolve to a real
     * armor piece for this slot. */
    private ItemStack buildDisguiseArmorItem(EquipmentSlot slot, int amount) {
        String materialName = config.disguiseArmorMaterial().toUpperCase() + ARMOR_SUFFIX.get(slot);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.matchMaterial("LEATHER" + ARMOR_SUFFIX.get(slot));
        }
        ItemStack disguise = new ItemStack(material != null ? material : Material.STICK, 1);
        ItemMeta meta = disguise.getItemMeta();
        meta.setDisplayName(ChatColor.RED + config.disguiseDisplayName());
        meta.setEnchantmentGlintOverride(config.disguiseGlint());
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        disguise.setItemMeta(meta);
        return disguise;
    }

    private boolean isDisguised(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    private record LockedItem(ItemLocation location, ItemStack original) {
    }
}
