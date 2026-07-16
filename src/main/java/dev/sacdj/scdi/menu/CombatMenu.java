package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class CombatMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public CombatMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Combat Settings");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.editable(Material.CLOCK, "Duration (ms)",
                String.valueOf(config.raw().getLong("combat.duration-ms"))));
        inv.setItem(10, MenuItems.toggle("Tag attacker", config.tagAttacker(),
                "Attacker also gets locked, not just the victim."));
        inv.setItem(11, MenuItems.toggle("Tag victim", config.tagVictim()));
        inv.setItem(12, MenuItems.toggle("PvE mode", config.pveMode(),
                "Also lock on mob damage in either direction."));
        inv.setItem(13, MenuItems.toggle("Retag resets timer", config.retagResetsTimer()));
        inv.setItem(14, MenuItems.toggle("Reset on death", config.resetOnDeath()));
        inv.setItem(15, MenuItems.toggle("Ignore creative", config.ignoreCreative()));
        inv.setItem(16, MenuItems.toggle("Ranged attacks tag", config.rangedAttacksTag()));
        inv.setItem(18, MenuItems.toggle("Hit tagging enabled", config.hitTaggingEnabled(),
                "Master switch for all hit-based tagging."));
        inv.setItem(19, MenuItems.toggle("Team tag attacker", config.teamTagAttacker(),
                "Hitting a teammate still tags you."));
        inv.setItem(20, MenuItems.toggle("Team tag victim", config.teamTagVictim(),
                "Being hit by a teammate tags you."));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Type the new combat duration in milliseconds:");
                chatInput.awaitInput(player, input -> {
                    try {
                        long ms = Long.parseLong(input.trim());
                        config.set("combat.duration-ms", ms);
                        player.sendMessage(ChatColor.GREEN + "Combat duration set to " + ms + "ms.");
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
                    }
                    manager.open(player, new CombatMenu(config, chatInput));
                });
            }
            case 10 -> toggleAndRefresh(player, manager, "combat.tag-attacker", config.tagAttacker());
            case 11 -> toggleAndRefresh(player, manager, "combat.tag-victim", config.tagVictim());
            case 12 -> toggleAndRefresh(player, manager, "combat.pve-mode", config.pveMode());
            case 13 -> toggleAndRefresh(player, manager, "combat.retag-resets-timer", config.retagResetsTimer());
            case 14 -> toggleAndRefresh(player, manager, "combat.reset-on-death", config.resetOnDeath());
            case 15 -> toggleAndRefresh(player, manager, "combat.ignore-creative", config.ignoreCreative());
            case 16 -> toggleAndRefresh(player, manager, "combat.ranged-attacks-tag", config.rangedAttacksTag());
            case 18 -> toggleAndRefresh(player, manager, "combat.hit-tagging-enabled", config.hitTaggingEnabled());
            case 19 -> toggleAndRefresh(player, manager, "combat.team-tag-attacker", config.teamTagAttacker());
            case 20 -> toggleAndRefresh(player, manager, "combat.team-tag-victim", config.teamTagVictim());
            default -> {
            }
        }
    }

    private void toggleAndRefresh(Player player, MenuManager manager, String path, boolean current) {
        config.set(path, !current);
        manager.open(player, new CombatMenu(config, chatInput));
    }
}
