package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class MainMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public MainMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Combat Disabled Items");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(10, MenuItems.nav(Material.IRON_SWORD, "Combat", "Duration, tagging rules, PvE mode."));
        inv.setItem(12, MenuItems.nav(Material.FIREWORK_ROCKET, "Disabled Items", "Which items get locked."));
        inv.setItem(14, MenuItems.nav(Material.BARRIER, "Disguise", "What the locked item looks like."));
        inv.setItem(16, MenuItems.nav(Material.NOTE_BLOCK, "Sounds", "Combat/safe sound + pitch."));
        inv.setItem(22, MenuItems.nav(Material.OAK_SIGN, "Display", "Actionbar and title toggles."));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        MenuScreen next = switch (slot) {
            case 10 -> new CombatMenu(config, chatInput);
            case 12 -> new DisabledItemsMenu(config, chatInput);
            case 14 -> new DisguiseMenu(config, chatInput);
            case 16 -> new SoundsMenu(config, chatInput);
            case 22 -> new DisplayMenu(config, chatInput);
            default -> null;
        };
        if (next != null) {
            manager.open(player, next);
        }
    }
}
