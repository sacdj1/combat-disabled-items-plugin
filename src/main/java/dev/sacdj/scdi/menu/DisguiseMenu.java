package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class DisguiseMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public DisguiseMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Disguise Appearance");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.editable(Material.STICK, "Disguise item",
                config.raw().getString("disguise.item"), "The item's real type while locked out."));
        inv.setItem(10, MenuItems.editable(Material.BARRIER, "Disguise model",
                config.raw().getString("disguise.model"), "Purely visual - what it looks like, not its real type."));
        inv.setItem(11, MenuItems.editable(Material.NAME_TAG, "Display name",
                config.raw().getString("disguise.name")));
        inv.setItem(12, MenuItems.toggle("Enchant glint", config.disguiseGlint()));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> promptString(player, manager, "disguise.item", "the disguise item id (e.g. minecraft:stick)");
            case 10 -> promptString(player, manager, "disguise.model", "the disguise model id (e.g. minecraft:barrier)");
            case 11 -> promptString(player, manager, "disguise.name", "the disguise display name");
            case 12 -> {
                config.set("disguise.glint", !config.disguiseGlint());
                manager.open(player, new DisguiseMenu(config, chatInput));
            }
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
            manager.open(player, new DisguiseMenu(config, chatInput));
        });
    }
}
