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
 * Keeps a disguise item from leaving the player's own inventory - dropping
 * it, placing it as a block (if it happens to be a placeable material), or
 * moving it into a DIFFERENT inventory (a chest, another player's, a
 * villager trade, ...) all let it separate from {@link DisguiseManager}'s
 * tracking, and unlock() restoring the original on top of that is a real
 * item duplication bug, not just a cosmetic glitch - the player ends up
 * with the real item back AND the now-untracked disguise item as a free,
 * independent item.
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
        if (disguiseManager.isDisguised(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            warn(event.getPlayer());
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
