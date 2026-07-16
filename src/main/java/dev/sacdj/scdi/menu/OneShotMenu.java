package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class OneShotMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public OneShotMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "One-Shot Detection");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.toggle("Announce", config.oneShotAnnounce(),
                "Broadcast when a player's first hit of a fresh encounter is also the kill."));
        inv.setItem(10, MenuItems.toggle("Ignore tag", config.oneShotIgnoreTag(),
                "Every kill counts, not just fresh-encounter kills."));
        inv.setItem(11, MenuItems.toggle("Cooldown enabled", config.oneShotCooldownEnabled(),
                "Also require the victim to have been out of combat a while."));
        inv.setItem(12, MenuItems.editable(Material.CLOCK, "Cooldown (seconds)",
                String.valueOf(config.oneShotCooldownSeconds())));
        inv.setItem(13, MenuItems.toggle("No-tag attacker on kill", config.oneShotNoTagAttacker(),
                "A one-shot kill doesn't tag the attacker."));
        inv.setItem(14, MenuItems.toggle("No-tag victim on kill", config.oneShotNoTagVictim(),
                "A one-shot victim doesn't stay tagged."));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> toggleAndRefresh(player, manager, "one-shot.announce", config.oneShotAnnounce());
            case 10 -> toggleAndRefresh(player, manager, "one-shot.ignore-tag", config.oneShotIgnoreTag());
            case 11 -> toggleAndRefresh(player, manager, "one-shot.cooldown-enabled", config.oneShotCooldownEnabled());
            case 12 -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Type the new cooldown in seconds:");
                chatInput.awaitInput(player, input -> {
                    try {
                        config.set("one-shot.cooldown-seconds", Long.parseLong(input.trim()));
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
                    }
                    manager.open(player, new OneShotMenu(config, chatInput));
                });
            }
            case 13 -> toggleAndRefresh(player, manager, "one-shot.no-tag-attacker-on-kill", config.oneShotNoTagAttacker());
            case 14 -> toggleAndRefresh(player, manager, "one-shot.no-tag-victim-on-kill", config.oneShotNoTagVictim());
            default -> {
            }
        }
    }

    private void toggleAndRefresh(Player player, MenuManager manager, String path, boolean current) {
        config.set(path, !current);
        manager.open(player, new OneShotMenu(config, chatInput));
    }
}
