package dev.sacdj.scdi.disguise;

import dev.sacdj.scdi.config.ScdiConfig;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final NamespacedKey markerKey;
    private final ScdiConfig config;

    private final Map<UUID, List<LockedItem>> locked = new ConcurrentHashMap<>();

    public DisguiseManager(JavaPlugin plugin, ScdiConfig config) {
        this.markerKey = new NamespacedKey(plugin, "disguised");
        this.config = config;
    }

    public void lock(Player player) {
        locked.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        List<LockedItem> items = locked.get(player.getUniqueId());
        PlayerInventory inv = player.getInventory();

        lockIfMatch(items, inv, new ItemLocation.Equipment(EquipmentSlot.HAND));
        lockIfMatch(items, inv, new ItemLocation.Equipment(EquipmentSlot.OFF_HAND));

        if (config.disableElytra()) {
            lockIfMatch(items, inv, new ItemLocation.Equipment(EquipmentSlot.CHEST));
        }

        if (config.scanFullInventory()) {
            for (int i = 0; i < 36; i++) {
                lockIfMatch(items, inv, new ItemLocation.InventorySlot(i));
            }
        }
    }

    public void unlock(Player player) {
        List<LockedItem> items = locked.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (LockedItem item : items) {
            item.location().write(inv, item.original());
        }
    }

    private void lockIfMatch(List<LockedItem> items, PlayerInventory inv, ItemLocation loc) {
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
        loc.write(inv, buildDisguiseItem(current.getAmount()));
    }

    private boolean matchesDisabledItem(ItemStack item, ItemLocation loc) {
        Material type = item.getType();
        if (loc instanceof ItemLocation.Equipment eq && eq.slot() == EquipmentSlot.CHEST) {
            return config.disableElytra() && type == Material.ELYTRA;
        }
        if (type == Material.FIREWORK_ROCKET) {
            return config.disableFireworkRocket();
        }
        if (type == Material.WIND_CHARGE) {
            return config.disableWindCharge();
        }
        return false;
    }

    private ItemStack buildDisguiseItem(int amount) {
        ItemStack disguise = new ItemStack(Material.STICK, amount);
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
