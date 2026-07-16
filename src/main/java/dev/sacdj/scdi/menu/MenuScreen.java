package dev.sacdj.scdi.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public interface MenuScreen {

    Inventory build(Player player);

    void onClick(Player player, int slot, MenuManager manager);
}
