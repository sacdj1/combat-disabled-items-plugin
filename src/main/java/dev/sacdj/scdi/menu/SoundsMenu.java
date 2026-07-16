package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class SoundsMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public SoundsMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Sounds");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.editable(Material.NOTE_BLOCK, "Combat sound",
                config.raw().getString("sounds.combat")));
        inv.setItem(10, MenuItems.editable(Material.CLOCK, "Combat pitch",
                String.valueOf(config.raw().getDouble("sounds.combat-pitch"))));
        inv.setItem(11, MenuItems.editable(Material.NOTE_BLOCK, "Safe sound",
                config.raw().getString("sounds.safe")));
        inv.setItem(12, MenuItems.editable(Material.CLOCK, "Safe pitch",
                String.valueOf(config.raw().getDouble("sounds.safe-pitch"))));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> promptString(player, manager, "sounds.combat", "the combat sound id (e.g. block.note_block.bit)");
            case 10 -> promptDouble(player, manager, "sounds.combat-pitch", "the combat pitch (0.5-2.0)");
            case 11 -> promptString(player, manager, "sounds.safe", "the safe sound id");
            case 12 -> promptDouble(player, manager, "sounds.safe-pitch", "the safe pitch (0.5-2.0)");
            default -> {
            }
        }
    }

    private void promptString(Player player, MenuManager manager, String path, String prompt) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type " + prompt + ":");
        chatInput.awaitInput(player, input -> {
            config.set(path, input.trim());
            player.sendMessage(ChatColor.GREEN + "Updated.");
            manager.open(player, new SoundsMenu(config, chatInput));
        });
    }

    private void promptDouble(Player player, MenuManager manager, String path, String prompt) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type " + prompt + ":");
        chatInput.awaitInput(player, input -> {
            try {
                config.set(path, Double.parseDouble(input.trim()));
                player.sendMessage(ChatColor.GREEN + "Updated.");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
            }
            manager.open(player, new SoundsMenu(config, chatInput));
        });
    }
}
