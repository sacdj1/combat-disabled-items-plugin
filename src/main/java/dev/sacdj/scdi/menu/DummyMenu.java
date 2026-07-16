package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class DummyMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public DummyMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 36, "Dummy Settings");
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(9, MenuItems.toggle("Allow player spawn", config.dummyAllowPlayerSpawn(),
                "Let non-admins use /scdi dummy spawn."));
        inv.setItem(10, MenuItems.editable(Material.CLOCK, "Spawn cooldown (s)",
                String.valueOf(config.dummySpawnCooldownSeconds())));
        inv.setItem(11, MenuItems.editable(Material.PLAYER_HEAD, "Max per player",
                String.valueOf(config.dummyMaxPerPlayer())));
        inv.setItem(12, MenuItems.editable(Material.CHEST, "Max total",
                String.valueOf(config.dummyMaxTotal())));
        inv.setItem(13, MenuItems.editable(Material.GOLDEN_APPLE, "Max health (buffer)",
                String.valueOf(config.dummyMaxHealth())));
        inv.setItem(14, MenuItems.editable(Material.DIAMOND_SWORD, "One-shot damage",
                String.valueOf(config.dummyOneShotDamage())));
        inv.setItem(15, MenuItems.toggle("Invincible by default", config.dummyInvincibleDefault()));
        inv.setItem(16, MenuItems.toggle("Immobile", config.dummyImmobile()));
        inv.setItem(18, MenuItems.toggle("Look at player", config.dummyLookAtPlayer()));
        inv.setItem(19, MenuItems.toggle("Regen enabled", config.dummyRegenEnabled()));
        inv.setItem(20, MenuItems.toggle("Show health", config.dummyShowHealth()));
        inv.setItem(21, MenuItems.toggle("Damage numbers", config.dummyDamageNumbers()));
        inv.setItem(22, MenuItems.toggle("Announce one-shot", config.dummyAnnounceOneShot()));
        inv.setItem(23, MenuItems.toggle("Announce time-to-kill", config.dummyAnnounceTimeToKill()));
        inv.setItem(24, MenuItems.toggle("Pickup items", config.dummyPickupItems()));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        switch (slot) {
            case 0 -> manager.open(player, new MainMenu(config, chatInput));
            case 9 -> toggleAndRefresh(player, manager, "dummy.allow-player-spawn", config.dummyAllowPlayerSpawn());
            case 10 -> editLong(player, manager, "dummy.spawn-cooldown-seconds", "spawn cooldown (seconds)");
            case 11 -> editLong(player, manager, "dummy.max-per-player", "max per player");
            case 12 -> editLong(player, manager, "dummy.max-total", "max total");
            case 13 -> editDouble(player, manager, "dummy.max-health", "max health");
            case 14 -> editDouble(player, manager, "dummy.one-shot-damage", "one-shot damage");
            case 15 -> toggleAndRefresh(player, manager, "dummy.invincible-default", config.dummyInvincibleDefault());
            case 16 -> toggleAndRefresh(player, manager, "dummy.immobile", config.dummyImmobile());
            case 18 -> toggleAndRefresh(player, manager, "dummy.look-at-player", config.dummyLookAtPlayer());
            case 19 -> toggleAndRefresh(player, manager, "dummy.regen-enabled", config.dummyRegenEnabled());
            case 20 -> toggleAndRefresh(player, manager, "dummy.show-health", config.dummyShowHealth());
            case 21 -> toggleAndRefresh(player, manager, "dummy.damage-numbers", config.dummyDamageNumbers());
            case 22 -> toggleAndRefresh(player, manager, "dummy.announce-one-shot", config.dummyAnnounceOneShot());
            case 23 -> toggleAndRefresh(player, manager, "dummy.announce-time-to-kill", config.dummyAnnounceTimeToKill());
            case 24 -> toggleAndRefresh(player, manager, "dummy.pickup-items", config.dummyPickupItems());
            default -> {
            }
        }
    }

    private void toggleAndRefresh(Player player, MenuManager manager, String path, boolean current) {
        config.set(path, !current);
        manager.open(player, new DummyMenu(config, chatInput));
    }

    private void editLong(Player player, MenuManager manager, String path, String label) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type the new " + label + ":");
        chatInput.awaitInput(player, input -> {
            try {
                config.set(path, Long.parseLong(input.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
            }
            manager.open(player, new DummyMenu(config, chatInput));
        });
    }

    private void editDouble(Player player, MenuManager manager, String path, String label) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type the new " + label + ":");
        chatInput.awaitInput(player, input -> {
            try {
                config.set(path, Double.parseDouble(input.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Not a number - unchanged.");
            }
            manager.open(player, new DummyMenu(config, chatInput));
        });
    }
}
