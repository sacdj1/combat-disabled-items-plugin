package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Default warning preferences new players start with - each player can
 * override their own via /scdi settings. */
public final class WarningsMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public WarningsMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Warning Defaults");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.toggle("Armor warning", config.armorWarningDefault()));
        inv.setItem(10, MenuItems.toggle("Armor warning sound", config.armorWarningSoundDefault()));
        inv.setItem(12, MenuItems.toggle("Inventory warning", config.inventoryWarningDefault()));
        inv.setItem(13, MenuItems.toggle("Inventory warning sound", config.inventoryWarningSoundDefault()));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> toggleAndRefresh(player, manager, "warnings.armor-warning", config.armorWarningDefault());
            case 10 -> toggleAndRefresh(player, manager, "warnings.armor-warning-sound", config.armorWarningSoundDefault());
            case 12 -> toggleAndRefresh(player, manager, "warnings.inventory-warning", config.inventoryWarningDefault());
            case 13 -> toggleAndRefresh(player, manager, "warnings.inventory-warning-sound", config.inventoryWarningSoundDefault());
            default -> {
            }
        }
    }

    private void toggleAndRefresh(Player player, MenuManager manager, String path, boolean current) {
        config.set(path, !current);
        manager.open(player, new WarningsMenu(config, chatInput));
    }
}
