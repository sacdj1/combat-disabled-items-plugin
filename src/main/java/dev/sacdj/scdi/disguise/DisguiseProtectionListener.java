package dev.sacdj.scdi.disguise;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;

/**
 * Keeps a disguise item from ending up somewhere {@link DisguiseManager}
 * can't cheaply find it again - placing it as a block, or moving it into a
 * DIFFERENT inventory (a chest, another player's, a villager trade, ...)
 * both get cancelled outright, since either would separate it from
 * tracking and let unlock() hand back the real item while the untracked
 * disguise item survives independently as a free duplicate. Dropping is
 * allowed instead of blocked - {@link DisguiseManager#trackDrop} records
 * the dropped entity's own id so unlock() can find and revert it later
 * with an O(1) lookup instead of scanning the world.
 *
 * <p>Deliberately does NOT block moving/reordering a disguise item WITHIN
 * the player's own inventory (hotbar order, which armor slot, hand swap) -
 * {@link DisguiseManager} tracks each disguise item by its own unique
 * instance id now, not a fixed slot, specifically so a player isn't locked
 * out of organizing their own inventory while tagged. Only the SPECIFIC
 * click/drag that would actually move the item into an external inventory
 * gets cancelled - having a chest open at all doesn't blanket-block
 * everything else just because a disguise item happens to be on the
 * cursor or in the clicked slot at the time.
 */
public final class DisguiseProtectionListener implements Listener {

    private final DisguiseManager disguiseManager;

    public DisguiseProtectionListener(DisguiseManager disguiseManager) {
        this.disguiseManager = disguiseManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        String instanceId = disguiseManager.instanceIdOf(event.getItemDrop().getItemStack());
        if (instanceId != null) {
            disguiseManager.trackDrop(instanceId, event.getItemDrop().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        boolean involvesDisguise = disguiseManager.isDisguised(event.getCurrentItem())
                || disguiseManager.isDisguised(event.getCursor());
        if (!involvesDisguise) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            // only the player's own inventory screen is open at all -
            // nothing external for this click to move the item into.
            return;
        }
        boolean clickedExternalInventory = top.equals(event.getClickedInventory());
        boolean shiftClickWouldMoveIt = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        if (clickedExternalInventory || shiftClickWouldMoveIt) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                warn(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        boolean involvesDisguise = false;
        for (var item : event.getNewItems().values()) {
            if (disguiseManager.isDisguised(item)) {
                involvesDisguise = true;
                break;
            }
        }
        if (!involvesDisguise) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            return;
        }
        int topSize = top.getSize();
        boolean touchesExternalInventory = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesExternalInventory) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                warn(player);
            }
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
        player.sendMessage(ChatColor.RED + "You can't move a disabled item outside your own inventory.");
    }
}
