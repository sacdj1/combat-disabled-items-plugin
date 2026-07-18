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
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps a disguise item from ending up somewhere {@link DisguiseManager}
 * can't cheaply find it again - placing it as a block, moving it into a
 * DIFFERENT inventory (a chest, another player's, a villager trade, ...),
 * or SPLITTING a tracked stack (partial drop, right-click "take half",
 * drag-splitting) all get cancelled outright. Any of these would separate
 * (a fragment of) the stack from tracking, and unlock() restoring the full
 * original on top of that is a real item duplication bug, not just a
 * cosmetic glitch - found live twice this session, both times because a
 * stack got split and one fragment was left behind, untracked, while the
 * player got the FULL original back anyway. Dropping is allowed for a
 * WHOLE stack - {@link DisguiseManager#trackDrop} records the dropped
 * entity's own id so unlock() can find and revert it later.
 *
 * <p>Deliberately does NOT block moving/reordering a disguise item WITHIN
 * the player's own inventory (hotbar order, which armor slot, hand swap) -
 * {@link DisguiseManager} tracks each disguise item by its own unique
 * instance id, not a fixed slot, specifically so a player isn't locked out
 * of organizing their own inventory while tagged. Only actions that would
 * split the stack or move it externally get cancelled.
 */
public final class DisguiseProtectionListener implements Listener {

    /** Every {@link InventoryAction} that's guaranteed to move a WHOLE stack
     * without splitting it - everything else involving a disguised item is
     * blocked. Safer to allowlist the known-safe actions than to try and
     * enumerate every possible partial/split action and risk missing one. */
    private static final Set<InventoryAction> WHOLE_STACK_ACTIONS = EnumSet.of(
            InventoryAction.NOTHING,
            InventoryAction.PICKUP_ALL,
            InventoryAction.PLACE_ALL,
            InventoryAction.SWAP_WITH_CURSOR,
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.HOTBAR_MOVE_AND_READD,
            InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ALL_SLOT
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
            // a partial drop (single-item Q-press on a bigger stack) - the
            // remainder left behind in inventory keeps the SAME instance id
            // (Bukkit copies persistent data onto both halves of a split),
            // so unlock() would find that remainder first and restore the
            // full original there, leaving this dropped fragment behind
            // forever as an inert, permanently-disguised orphan. Block the
            // split instead of trying to track two fragments of one id.
            event.setCancelled(true);
            warn(event.getPlayer());
            return;
        }
        disguiseManager.trackDrop(instanceId, event.getItemDrop().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        boolean involvesDisguise = disguiseManager.isDisguised(currentItem) || disguiseManager.isDisguised(cursorItem);
        if (!involvesDisguise) {
            return;
        }

        if (!WHOLE_STACK_ACTIONS.contains(event.getAction())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                warn(player);
            }
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
        // dragging is fundamentally a "distribute a cursor stack across
        // multiple slots" operation - there's no legitimate whole-stack-only
        // drag that plain clicking doesn't already cover, so any drag of a
        // disguised stack is blocked outright rather than trying to prove
        // it wouldn't split anything.
        if (disguiseManager.isDisguised(event.getOldCursor())) {
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
        player.sendMessage(ChatColor.RED + "You can't split or move a disabled item outside your own inventory.");
    }
}
