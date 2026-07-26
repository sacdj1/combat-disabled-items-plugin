package dev.sacdj.scdi.disguise;

import io.papermc.paper.event.block.CompostItemEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * <p>Everything below is blocked outright rather than tracked, because each
 * one either destroys/consumes the item through a path {@link
 * DisguiseManager#reconcile} can't see (nothing to reconcile afterward - the
 * item's just gone), or happens through an event that isn't even an
 * inventory click/drag in the first place:
 * <ul>
 *   <li>Placing a disguise item as a block - that's functional USE of the
 *   item, not just storing/moving it, and disabling functional use is the
 *   actual point of the feature.</li>
 *   <li>Moving into a "consuming" inventory (a villager trade, a furnace,
 *   an anvil, a brewing stand, ...) - those can remove an item by a
 *   mechanism other than plain relocation (traded away, smelted, combined)
 *   just by clicking elsewhere in the SAME view. Left untracked, that both
 *   lets a worthless disguise stand-in be "spent" for real value AND still
 *   hands the real original back at unlock - see
 *   {@link #SAFE_EXTERNAL_TYPES}.</li>
 *   <li>Composting - a direct block interaction, not an inventory click at
 *   all; same "spend the decoy, still get the original back" problem as
 *   above.</li>
 *   <li>Equipping onto an armor stand, or placing into an item frame - also
 *   direct entity interactions, not inventory events.</li>
 *   <li>Hopper (or other block-automation) transfers - no player and no
 *   inventory click involved, so nothing here would ever notice the item
 *   moving.</li>
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

        // someone else's tracked item turning up in THIS click - taken out
        // of a shared chest/barrel/... by a stranger - reveals to the real
        // item right here instead of silently becoming an untracked decoy
        // in their inventory while the real owner still gets a fallback
        // replacement later. A no-op if it's actually the clicker's own.
        // Explicitly written back via the event setters rather than trusting
        // in-place mutation of whatever getCurrentItem()/getCursor() handed
        // back, since that's not guaranteed to be a live reference.
        if (disguiseManager.revealForOtherPlayer(player, currentItem)) {
            event.setCurrentItem(currentItem);
        }
        if (disguiseManager.revealForOtherPlayer(player, cursorItem)) {
            event.setCursor(cursorItem);
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

    /** Composting consumes one unit of the item on every right-click
     * (success chance only governs whether the compost level actually
     * rises, not whether the item's spent) via a plain block interaction -
     * not an inventory click, so nothing else here would ever see it. Left
     * unblocked, that's a real duplication-of-value exploit: spend the
     * worthless disguise decoy for a genuine compost-level increase, then
     * still get the real original handed back at unlock anyway, since
     * nothing tracked the item being destroyed. Blocked at the interaction
     * itself (before {@link CompostItemEvent} even fires) rather than
     * trying to undo it after - CompostItemEvent isn't even cancellable. */
    @EventHandler(ignoreCancelled = true)
    public void onComposterInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || block == null || block.getType() != Material.COMPOSTER) {
            return;
        }
        if (disguiseManager.isDisguised(event.getItem())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    /** Equipping a disguised item onto an armor stand happens via a direct
     * entity right-click, not an inventory click - completely outside
     * everything above. Only blocks PLACING (getPlayerItem, what's about
     * to go ONTO the stand); taking an already-worn item back OFF is left
     * alone so a disguise item that somehow ended up on a stand some other
     * way isn't trapped there forever. */
    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (disguiseManager.isDisguised(event.getPlayerItem())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    /** Same idea for item frames - placing an item into one is its own
     * event, not an inventory click. Only blocks PLACE, for the same
     * "don't trap an existing one" reason as the armor stand case above. */
    @EventHandler(ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE
                && disguiseManager.isDisguised(event.getItemStack())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    /** Hopper (or hopper-minecart/dropper-pushed) automation moving a
     * disguised item between inventories happens with no player and no
     * inventory click at all - reconcile() never runs, so
     * externalInstances can go stale the moment automation quietly moves
     * the item out from under wherever it was last seen. Blocked outright
     * rather than taught to follow it: nobody needs a disabled item
     * automatically sorted mid-fight, and this is a much smaller cost than
     * either losing track of it or opening a new way to separate it from
     * tracking. */
    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (disguiseManager.isDisguised(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private void warn(Player player) {
        player.sendMessage(ChatColor.RED + "You can't do that with a disabled item right now.");
    }
}
