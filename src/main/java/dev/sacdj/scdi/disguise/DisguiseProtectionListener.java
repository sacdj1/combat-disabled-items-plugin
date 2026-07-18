package dev.sacdj.scdi.disguise;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps a disguised item's tracking correct wherever a player takes it,
 * instead of blocking movement outright. Dropping (whole or partial),
 * moving into a chest/barrel/dispenser/other storage inventory, and
 * drag-splitting are all allowed - {@link DisguiseManager#splitFragment}
 * and {@link DisguiseManager#reconcile} do the actual work of keeping each
 * physical fragment independently trackable/restorable no matter where it
 * ends up, rather than this listener trying to prevent every way a stack
 * could split or travel.
 *
 * <p>If someone OTHER than the tracked owner picks a dropped disguise item
 * up first, it reveals to the real item right there for them - see {@link
 * DisguiseManager#revealForOtherPlayer}. Dropping it already meant losing
 * it, same as any other item; without this a stranger's pickup would leave
 * an inert, permanently-disguised decoy in their inventory forever while
 * the original owner STILL got a fresh replacement handed to them at
 * unlock.
 *
 * <p>Two things still ARE blocked outright:
 * <ul>
 *   <li>Placing a disguise item as a block - that's functional USE of the
 *   item, not just storing/moving it, and disabling functional use is the
 *   actual point of the feature.</li>
 *   <li>Moving into a "consuming" inventory (a villager trade, a furnace,
 *   an anvil, a brewing stand, ...) - those can remove an item by a
 *   mechanism other than plain relocation (traded away, smelted, combined)
 *   just by clicking elsewhere in the SAME view, which {@link
 *   DisguiseManager#reconcile}'s next-tick scan can't reliably see through.
 *   Left untracked, that both lets a worthless disguise stand-in be
 *   "spent" for real value AND still hands the real original back at
 *   unlock - see {@link #SAFE_EXTERNAL_TYPES}.</li>
 * </ul>
 */
public final class DisguiseProtectionListener implements Listener {

    /** Inventory types items are allowed to move into while disguised -
     * plain storage, where clicking elsewhere in the same view can't make
     * the item disappear by anything other than relocating it (which
     * {@link DisguiseManager#reconcile} can always find and re-track).
     * Deliberately an allowlist, not a blocklist: anything NOT in here
     * (merchant trades, furnaces, anvils, grindstones, brewing stands,
     * enchanting tables, ...) stays blocked by default rather than risking
     * an inventory type this list didn't anticipate. */
    private static final Set<InventoryType> SAFE_EXTERNAL_TYPES = EnumSet.of(
            InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX,
            InventoryType.ENDER_CHEST, InventoryType.DISPENSER, InventoryType.DROPPER,
            InventoryType.HOPPER, InventoryType.PLAYER
    );

    private final DisguiseManager disguiseManager;

    public DisguiseProtectionListener(DisguiseManager disguiseManager) {
        this.disguiseManager = disguiseManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        String instanceId = disguiseManager.instanceIdOf(dropped);
        if (instanceId == null) {
            return;
        }
        if (!disguiseManager.isFullStack(dropped)) {
            // a partial drop (single-item Q-press on a bigger stack) - used
            // to be blocked outright; now the departing piece just gets its
            // own independent instance id instead, decoupling it from
            // whatever's left behind in inventory.
            instanceId = disguiseManager.splitFragment(event.getPlayer(), instanceId, dropped, dropped.getAmount());
            event.getItemDrop().setItemStack(dropped);
        }
        disguiseManager.trackDrop(instanceId, event.getItemDrop().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) {
            return;
        }
        disguiseManager.revealForOtherPlayer(picker, event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        boolean involvesDisguise = disguiseManager.isDisguised(currentItem) || disguiseManager.isDisguised(cursorItem);
        if (!involvesDisguise) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        boolean external = top.getType() != InventoryType.CRAFTING;
        if (external) {
            // only block if THIS click actually reaches into the unsafe top
            // inventory - an external (even unsafe) inventory merely being
            // open shouldn't stop the player from freely rearranging their
            // OWN inventory underneath it.
            boolean touchesTop = top.equals(event.getClickedInventory())
                    || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
            if (touchesTop && !SAFE_EXTERNAL_TYPES.contains(top.getType())) {
                event.setCancelled(true);
                warn(player);
                return;
            }
        }
        disguiseManager.scheduleReconcile(player, external ? top : null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!disguiseManager.isDisguised(event.getOldCursor())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        boolean external = top.getType() != InventoryType.CRAFTING;
        if (external) {
            boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
            if (touchesTop && !SAFE_EXTERNAL_TYPES.contains(top.getType())) {
                event.setCancelled(true);
                warn(player);
                return;
            }
        }
        disguiseManager.scheduleReconcile(player, external ? top : null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (disguiseManager.isDisguised(event.getItemInHand())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    private void warn(Player player) {
        player.sendMessage(ChatColor.RED + "You can't do that with a disabled item right now.");
    }
}
