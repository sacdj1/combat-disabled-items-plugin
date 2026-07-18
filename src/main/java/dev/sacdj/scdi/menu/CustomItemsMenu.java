package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Any extra item to disable while tagged, on top of the built-ins
 * (firework rockets/wind charges/elytra) - a real, unlimited list, same
 * idea as the datapack's disguise_targets. Each rule can match by material,
 * by enchantment, or both together (e.g. "any bow" vs "specifically a bow
 * enchanted with Power"). Each entry shown as its own clickable icon (click
 * removes it); a fixed "+ Add" button prompts chat for
 * "<material|*> [enchantment|*]". */
public final class CustomItemsMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;
    private final List<ScdiConfig.CustomItemRule> rules;

    public CustomItemsMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
        this.rules = new ArrayList<>(config.customDisabledItems());
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 54, "Custom Disabled Items");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(0, MenuItems.nav(Material.ARROW, "<- Back"));
        inv.setItem(4, addButton());

        int slot = 9;
        for (ScdiConfig.CustomItemRule rule : rules) {
            if (slot >= 54) {
                break;
            }
            inv.setItem(slot, ruleItem(rule));
            slot++;
        }
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        if (slot == 0) {
            manager.open(player, new DisabledItemsMenu(config, chatInput));
            return;
        }
        if (slot == 4) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW
                    + "Type: <material|*> [enchantment|*] - e.g. \"ENDER_PEARL\", \"* sharpness\", or \"bow power\":");
            chatInput.awaitInput(player, input -> {
                String[] parts = input.trim().split("\\s+");
                Material material = parseMaterial(parts.length > 0 ? parts[0] : "*");
                Enchantment enchantment = parts.length > 1 ? parseEnchantment(parts[1]) : null;
                boolean materialGiven = parts.length > 0 && !parts[0].equals("*");
                boolean enchantGiven = parts.length > 1 && !parts[1].equals("*");
                if (materialGiven && material == null) {
                    player.sendMessage(ChatColor.RED + "Unknown material: " + parts[0]);
                } else if (enchantGiven && enchantment == null) {
                    player.sendMessage(ChatColor.RED + "Unknown enchantment: " + parts[1]);
                } else if (material == null && enchantment == null) {
                    player.sendMessage(ChatColor.RED + "Need at least a material or an enchantment.");
                } else {
                    config.addCustomDisabledItem(material, enchantment);
                    player.sendMessage(ChatColor.GREEN + "Added.");
                }
                manager.open(player, new CustomItemsMenu(config, chatInput));
            });
            return;
        }
        int index = slot - 9;
        if (index >= 0 && index < rules.size()) {
            config.removeCustomDisabledItem(index);
            player.sendMessage(ChatColor.GREEN + "Removed.");
            manager.open(player, new CustomItemsMenu(config, chatInput));
        }
    }

    private Material parseMaterial(String arg) {
        return arg.equals("*") ? null : Material.matchMaterial(arg);
    }

    private Enchantment parseEnchantment(String arg) {
        if (arg.equals("*")) {
            return null;
        }
        return org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(arg.toLowerCase(Locale.ROOT)));
    }

    private ItemStack addButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "+ Add rule");
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + "Click, then type in chat:",
                ChatColor.DARK_GRAY + "<material|*> [enchantment|*]"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack ruleItem(ScdiConfig.CustomItemRule rule) {
        Material icon = rule.material() != null && rule.material().isItem() ? rule.material() : Material.ENCHANTED_BOOK;
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        String materialPart = rule.material() != null ? rule.material().name() : "Any item";
        String enchantPart = rule.enchantment() != null ? " + " + rule.enchantment().getKey().getKey() : "";
        meta.setDisplayName(ChatColor.AQUA + materialPart + ChatColor.LIGHT_PURPLE + enchantPart);
        meta.setLore(List.of(ChatColor.DARK_GRAY + "Click to remove."));
        item.setItemMeta(meta);
        return item;
    }
}
