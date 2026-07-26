package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class DisabledItemsMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public DisabledItemsMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Disabled Items");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.toggle("Firework rockets", config.disableFireworkRocket()));
        inv.setItem(10, MenuItems.toggle("Wind charges", config.disableWindCharge()));
        inv.setItem(11, MenuItems.toggle("Elytra", config.disableElytra(),
                "Only the worn chestplate slot."));
        inv.setItem(12, MenuItems.toggle("Scan hotbar", config.scanHotbar(),
                "Also lock a matching item anywhere in the hotbar, not just held/worn."));
        inv.setItem(14, MenuItems.toggle("Scan backpack", config.scanExtendedInventory(),
                "Also lock a matching item anywhere in the rest of the backpack."));
        inv.setItem(13, MenuItems.toggle("Worn armor", config.disableArmor(),
                "All 4 armor slots, not just elytra."));
        inv.setItem(15, MenuItems.nav(Material.CHEST, "Custom Items",
                config.customDisabledItems().size() + " configured - click to manage."));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> toggleAndRefresh(player, manager, "disabled-items.firework-rocket", config.disableFireworkRocket());
            case 10 -> toggleAndRefresh(player, manager, "disabled-items.wind-charge", config.disableWindCharge());
            case 11 -> toggleAndRefresh(player, manager, "disabled-items.elytra", config.disableElytra());
            case 12 -> toggleAndRefresh(player, manager, "disabled-items.scan-hotbar", config.scanHotbar());
            case 14 -> toggleAndRefresh(player, manager, "disabled-items.scan-extended-inventory", config.scanExtendedInventory());
            case 13 -> toggleAndRefresh(player, manager, "disabled-items.armor", config.disableArmor());
            case 15 -> manager.open(player, new CustomItemsMenu(config, chatInput));
            default -> {
            }
        }
    }

    private void toggleAndRefresh(Player player, MenuManager manager, String path, boolean current) {
        config.set(path, !current);
        manager.open(player, new DisabledItemsMenu(config, chatInput));
    }
}
