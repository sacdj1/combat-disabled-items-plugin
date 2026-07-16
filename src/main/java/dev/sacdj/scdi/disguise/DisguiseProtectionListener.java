package dev.sacdj.scdi.disguise;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Keeps a disguise item pinned to exactly the slot {@link DisguiseManager}
 * is tracking it in - dropping, moving, swapping to the other hand, or
 * placing one (if it happens to be a placeable block) all let it separate
 * from that tracking, and {@link DisguiseManager#unlock} restoring the
 * original unconditionally on top of that is a real item duplication bug,
 * not just a cosmetic glitch: the player ends up with the real item back
 * AND the now-untracked disguise item as a free, independent item. Blocking
 * every way to touch a disguise item is cheaper and more robust than trying
 * to detect and undo it after the fact.
 */
public final class DisguiseProtectionListener implements Listener {

    private final DisguiseManager disguiseManager;

    public DisguiseProtectionListener(DisguiseManager disguiseManager) {
        this.disguiseManager = disguiseManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (disguiseManager.isDisguised(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (disguiseManager.isDisguised(event.getCurrentItem()) || disguiseManager.isDisguised(event.getCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                warn(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        for (var item : event.getNewItems().values()) {
            if (disguiseManager.isDisguised(item)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    warn(player);
                }
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (disguiseManager.isDisguised(event.getMainHandItem()) || disguiseManager.isDisguised(event.getOffHandItem())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (disguiseManager.isDisguised(event.getItemInHand())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    private void warn(Player player) {
        player.sendMessage(ChatColor.RED + "You can't move a disabled item while it's locked.");
    }
}
