package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Any extra held item to disable while tagged, on top of the built-ins
 * (firework rockets/wind charges/elytra) - a real, unlimited list, same
 * idea as the datapack's disguise_targets. Each entry shown as its own
 * clickable icon (click removes it); a fixed "+ Add" button prompts chat
 * for a material name to add. */
public final class CustomItemsMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;
    private final List<Material> items;

    public CustomItemsMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
        this.items = new ArrayList<>(config.customDisabledItems());
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
        for (Material material : items) {
            if (slot >= 54) {
                break;
            }
            inv.setItem(slot, removableItem(material));
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
            player.sendMessage(ChatColor.YELLOW + "Type the material id to add (e.g. ENDER_PEARL):");
            chatInput.awaitInput(player, input -> {
                Material material = Material.matchMaterial(input.trim());
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "Unknown material: " + input.trim());
                } else {
                    config.addCustomDisabledItem(material);
                    player.sendMessage(ChatColor.GREEN + material.name() + " added.");
                }
                manager.open(player, new CustomItemsMenu(config, chatInput));
            });
            return;
        }
        int index = slot - 9;
        if (index >= 0 && index < items.size()) {
            Material material = items.get(index);
            config.removeCustomDisabledItem(material);
            player.sendMessage(ChatColor.GREEN + material.name() + " removed.");
            manager.open(player, new CustomItemsMenu(config, chatInput));
        }
    }

    private ItemStack addButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "+ Add item");
        meta.setLore(List.of(ChatColor.DARK_GRAY + "Click, then type a material id in chat."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack removableItem(Material material) {
        ItemStack item = new ItemStack(material.isItem() ? material : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + material.name());
        meta.setLore(List.of(ChatColor.DARK_GRAY + "Click to remove."));
        item.setItemMeta(meta);
        return item;
    }
}
