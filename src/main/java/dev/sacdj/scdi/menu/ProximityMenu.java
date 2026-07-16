package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class ProximityMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public ProximityMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Proximity Tagging");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.toggle("Enabled", config.proximityEnabled(),
                "Keep items disabled while another player stays nearby, no hit required."));
        inv.setItem(10, MenuItems.editable(Material.COMPASS, "Trigger distance",
                String.valueOf(config.proximityDistance())));
        inv.setItem(11, MenuItems.editable(Material.COMPASS, "Retag distance",
                String.valueOf(config.proximityRetagDistance())));
        inv.setItem(12, MenuItems.toggle("Role by movement", config.proximityRoleByMovement(),
                "Infer attacker/victim from who's moving more."));
        inv.setItem(13, MenuItems.toggle("Team tag proximity", config.teamTagProximity(),
                "Teammates proximity-tag each other too."));
        inv.setItem(14, MenuItems.editable(Material.CLOCK, "Check interval (ticks)",
                String.valueOf(config.proximityIntervalTicks())));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> toggleAndRefresh(player, manager, "proximity.enabled", config.proximityEnabled());
            case 10 -> editDouble(player, manager, "proximity.distance", "trigger distance");
            case 11 -> editDouble(player, manager, "proximity.retag-distance", "retag distance");
            case 12 -> toggleAndRefresh(player, manager, "proximity.role-by-movement", config.proximityRoleByMovement());
            case 13 -> toggleAndRefresh(player, manager, "proximity.team-tag-proximity", config.teamTagProximity());
            case 14 -> editLong(player, manager, "proximity.interval-ticks", "check interval");
            default -> {
            }
        }
    }

    private void toggleAndRefresh(Player player, MenuManager manager, String path, boolean current) {
        config.set(path, !current);
        manager.open(player, new ProximityMenu(config, chatInput));
    }

    private void editDouble(Player player, MenuManager manager, String path, String label) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type the new " + label + " (blocks):");
        chatInput.awaitInput(player, input -> {
            try {
                config.set(path, Double.parseDouble(input.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
            }
            manager.open(player, new ProximityMenu(config, chatInput));
        });
    }

    private void editLong(Player player, MenuManager manager, String path, String label) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type the new " + label + " (ticks):");
        chatInput.awaitInput(player, input -> {
            try {
                config.set(path, Long.parseLong(input.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
            }
            manager.open(player, new ProximityMenu(config, chatInput));
        });
    }
}
