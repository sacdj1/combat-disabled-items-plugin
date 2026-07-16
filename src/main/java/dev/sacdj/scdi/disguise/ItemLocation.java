package dev.sacdj.scdi.disguise;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Where a locked item came from, so it can go back to exactly the same spot -
 * a raw inventory slot index (hotbar/backpack) or a named equipment slot
 * (armor/offhand, which Bukkit exposes separately from the slot array).
 */
public sealed interface ItemLocation {

    ItemStack read(PlayerInventory inv);

    void write(PlayerInventory inv, ItemStack item);

    record InventorySlot(int index) implements ItemLocation {
        @Override
        public ItemStack read(PlayerInventory inv) {
            return inv.getItem(index);
        }

        @Override
        public void write(PlayerInventory inv, ItemStack item) {
            inv.setItem(index, item);
        }
    }

    record Equipment(EquipmentSlot slot) implements ItemLocation {
        @Override
        public ItemStack read(PlayerInventory inv) {
            return inv.getItem(slot);
        }

        @Override
        public void write(PlayerInventory inv, ItemStack item) {
            inv.setItem(slot, item);
        }
    }
}
