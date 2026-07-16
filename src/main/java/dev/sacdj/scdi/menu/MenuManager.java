package dev.sacdj.scdi.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuManager implements Listener {

    private final JavaPlugin plugin;

    public MenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, MenuScreen screen) {
        Inventory inventory = screen.build(player);
        if (inventory.getHolder() instanceof ScdiMenuHolder holder) {
            holder.setInventory(inventory);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ScdiMenuHolder holder)) {
            return;
        }
        // it's our GUI - always cancel movement, this is a read/click-only
        // menu, not a place to store or take items.
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> holder.screen().onClick(player, slot, this));
    }
}
