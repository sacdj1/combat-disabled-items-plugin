package dev.sacdj.scdi.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Small builders for the item stacks a menu screen is made of. */
public final class MenuItems {

    private MenuItems() {
    }

    public static ItemStack toggle(String label, boolean value, String... lore) {
        ItemStack item = new ItemStack(value ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((value ? ChatColor.GREEN : ChatColor.RED) + label + ChatColor.GRAY + " ["
                + (value ? "ON" : "OFF") + "]");
        meta.setLore(withHint(lore, "Click to toggle."));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack editable(Material material, String label, String currentValue, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + label + ChatColor.GRAY + ": " + ChatColor.YELLOW + currentValue);
        meta.setLore(withHint(lore, "Click, then type the new value in chat."));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack nav(Material material, String label, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + label);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> withHint(String[] lore, String hint) {
        List<String> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(ChatColor.GRAY + line);
        }
        lines.add(ChatColor.DARK_GRAY + hint);
        return lines;
    }
}
