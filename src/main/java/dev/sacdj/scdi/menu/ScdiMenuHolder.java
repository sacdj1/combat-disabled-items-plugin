package dev.sacdj.scdi.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker so MenuManager's click listener can recognise "this inventory is one
 * of ours" (via instanceof) and find its way back to the MenuScreen that
 * built it, without needing a separate open-screen-per-player map.
 */
public final class ScdiMenuHolder implements InventoryHolder {

    private final MenuScreen screen;
    private Inventory inventory;

    public ScdiMenuHolder(MenuScreen screen) {
        this.screen = screen;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public MenuScreen screen() {
        return screen;
    }
}
