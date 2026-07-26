package dev.sacdj.scdi.disguise;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.warning.WarningManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swaps real items for a disguise item and back. Unlike the datapack's
 * approach (rebuild an item from a captured NBT component snapshot via
 * commands - the source of the equippable-negation bug and its relatives),
 * this holds the actual original {@link ItemStack} object in memory and puts
 * the same object back on unlock. There's no serialization round-trip to lose
 * data in, so that whole bug class is structurally impossible here.
 *
 * <p>Each disguise item carries its own unique instance id (a random UUID
 * baked into its persistent data, separate from the plain boolean "this is
 * a disguise" marker), and restoring on unlock searches the player's WHOLE
 * inventory for whichever slot currently holds that id, rather than
 * remembering a fixed slot from lock-time. That's deliberate: a player has
 * to be free to reorganize their own inventory (hotbar order, which armor
 * slot, hand swap) while tagged without it breaking restoration or opening
 * a duplication hole.
 *
 * <p>Dropping, partially splitting a stack, and moving into another
 * (storage-type) inventory - a chest, a barrel, a dispenser, ... - are all
 * allowed too, not blocked outright the way an earlier version of this
 * class did. Two mechanisms make that safe: {@link #splitFragment} gives
 * a departing partial amount its OWN instance id the moment it splits off
 * (instead of letting Bukkit copy the same id onto both halves, which is
 * what caused a real duplication bug earlier this session), and
 * {@link #reconcile} re-derives tracking a tick after any inventory click/
 * drag that touched a disguised item, re-deriving one independently
 * restorable fragment per physical location it actually finds the id in.
 * {@link DisguiseProtectionListener} only still blocks placing a disguise
 * item as a block (that's functional USE, not just storage) and moving one
 * into a "consuming" inventory - a villager trade, a furnace, an anvil,
 * ... - where the item can be removed by a mechanism other than plain
 * relocation, which reconcile can't reliably see through.
 */
public final class DisguiseManager {

    private static final Map<EquipmentSlot, String> ARMOR_SUFFIX = new EnumMap<>(Map.of(
            EquipmentSlot.HEAD, "_HELMET",
            EquipmentSlot.CHEST, "_CHESTPLATE",
            EquipmentSlot.LEGS, "_LEGGINGS",
            EquipmentSlot.FEET, "_BOOTS"
    ));

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey instanceKey;
    private final ScdiConfig config;
    private final WarningManager warnings;

    private final Map<UUID, List<LockedItem>> locked = new ConcurrentHashMap<>();
    // instance id -> the dropped Item entity's own UUID, so unlock() can do
    // an O(1) Bukkit.getEntity() lookup instead of scanning every entity in
    // every world - populated the moment a disguised item is dropped (see
    // DisguiseProtectionListener), read/removed here on restore.
    private final Map<String, UUID> droppedInstances = new ConcurrentHashMap<>();
    // instance id -> the external (non-player) Inventory it was last found
    // in by reconcile() - a chest, barrel, dispenser, ... - so unlock() can
    // find and revert it there directly instead of only ever looking in the
    // player's own inventory or as a dropped entity.
    private final Map<String, Inventory> externalInstances = new ConcurrentHashMap<>();
    // instance id -> its LockedItem, independent of which player owns it -
    // lets DisguiseProtectionListener answer "would this action split a
    // tracked stack" in O(1) without knowing the owner. See isFullStack.
    private final Map<String, LockedItem> byInstanceId = new ConcurrentHashMap<>();
    // instance id -> the player who owns/tracks it - lets a pickup by
    // someone ELSE be told apart from the owner just picking their own
    // dropped item back up. See revealForOtherPlayer.
    private final Map<String, UUID> instanceOwners = new ConcurrentHashMap<>();
    private BukkitTask flashTask;
    private BukkitTask refreshTask;
    private long flashTickCounter;

    public DisguiseManager(JavaPlugin plugin, ScdiConfig config, WarningManager warnings) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "disguised");
        this.instanceKey = new NamespacedKey(plugin, "disguise_instance");
        this.config = config;
        this.warnings = warnings;
    }

    public void start() {
        flashTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFlash, 0L, 1L);
        // catches anything picked up/equipped AFTER the initial tag that
        // wasn't there to scan yet (e.g. unequip a rocket right before
        // getting hit, re-equip mid-fight) - without this, lock() only ever
        // ran once per tag/retag, leaving a real bypass for anyone who
        // timed their inventory around the hit. Every 10 ticks (2/sec) is
        // plenty responsive without scanning tagged players' inventories
        // every tick.
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRefresh, 0L, 10L);
    }

    public void stop() {
        if (flashTask != null) {
            flashTask.cancel();
        }
        if (refreshTask != null) {
            refreshTask.cancel();
        }
    }

    private void tickRefresh() {
        if (locked.isEmpty()) {
            return;
        }
        for (UUID id : locked.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                lock(player);
            }
        }
    }

    public void lock(Player player) {
        locked.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        List<LockedItem> items = locked.get(player.getUniqueId());
        PlayerInventory inv = player.getInventory();

        lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.HAND));
        lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.OFF_HAND));

        if (config.disableElytra()) {
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.CHEST));
        }
        if (config.disableArmor()) {
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.HEAD));
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.CHEST));
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.LEGS));
            lockIfMatch(player, items, inv, new ItemLocation.Equipment(EquipmentSlot.FEET));
        }

        if (config.scanFullInventory()) {
            for (int i = 0; i < 36; i++) {
                lockIfMatch(player, items, inv, new ItemLocation.InventorySlot(i));
            }
        }
    }

    public void unlock(Player player) {
        warnings.onCombatEnd(player);
        List<LockedItem> items = locked.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (LockedItem item : items) {
            byInstanceId.remove(item.instanceId());
            instanceOwners.remove(item.instanceId());
            if (restoreByInstanceId(player, inv, item)) {
                continue;
            }
            if (restoreDroppedInstance(item)) {
                continue;
            }
            if (restoreExternalInstance(item)) {
                continue;
            }
            // genuinely couldn't find the disguise item anywhere - already
            // picked up by someone else, burned, despawned, whatever. Never
            // silently lose the real item over it either way: hand the
            // original back wherever it fits, or drop it at their feet if
            // their inventory's completely full.
            Map<Integer, ItemStack> overflow = inv.addItem(item.original());
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    /** Public - {@link DisguiseProtectionListener} uses this to tell whether
     * a drop is the WHOLE tracked stack or only part of it, so it knows
     * whether {@link #splitFragment} needs to run first. A disguise item's
     * instance id lives on its {@link ItemMeta}, which Bukkit happily
     * copies onto BOTH halves of any split stack - restoring "the first
     * slot found" for a shared id used to hand back the FULL original
     * amount while leaving the other half behind forever as an inert,
     * permanently-disguised orphan (a real duplication bug found earlier
     * this session). {@link #splitFragment} is what actually fixes that, by
     * giving the departing fragment its own independent id the moment it
     * splits off. */
    public boolean isFullStack(ItemStack item) {
        String instanceId = instanceIdOf(item);
        if (instanceId == null) {
            return true;
        }
        LockedItem tracked = byInstanceId.get(instanceId);
        return tracked == null || item.getAmount() >= tracked.original().getAmount();
    }

    /** Marks a disguise item as dropped so {@link #unlock} can find it again
     * later - called from {@link DisguiseProtectionListener} the moment a
     * drop is allowed through. */
    public void trackDrop(String instanceId, UUID droppedEntityId) {
        droppedInstances.put(instanceId, droppedEntityId);
    }

    /** Splits {@code departAmount} off the tracked stack behind
     * {@code originalInstanceId} into its OWN, independently tracked
     * fragment - the departing physical stack's id is rewritten to a fresh
     * one so it no longer collides with whatever's left behind. Used the
     * moment part of a locked stack is about to leave the player's
     * inventory (a partial drop, a partial-amount click) instead of
     * blocking the split outright: Bukkit copies the SAME instance id onto
     * both halves of any ordinary split, and restoring "the first slot
     * found" for a shared id used to hand back the FULL original amount
     * while leaving the other half behind as a permanently-disguised
     * orphan - the actual duplication bug fixed earlier this session.
     * Splitting the tracking itself, not just blocking the action, is what
     * makes partial drops/moves safe instead of merely re-hiding the bug.
     *
     * @return the id the departing stack now carries (a fresh one), or the
     *         original id unchanged if nothing needed splitting.
     */
    public String splitFragment(Player player, String originalInstanceId, ItemStack departingStack, int departAmount) {
        LockedItem original = byInstanceId.get(originalInstanceId);
        if (original == null || departAmount >= original.original().getAmount()) {
            return originalInstanceId;
        }
        List<LockedItem> items = locked.get(player.getUniqueId());
        if (items == null) {
            return originalInstanceId;
        }
        int remainAmount = original.original().getAmount() - departAmount;
        items.remove(original);
        byInstanceId.remove(originalInstanceId);
        instanceOwners.remove(originalInstanceId);

        String departId = UUID.randomUUID().toString();
        ItemStack departOriginal = original.original().clone();
        departOriginal.setAmount(departAmount);
        LockedItem departFragment = new LockedItem(departId, departOriginal);
        items.add(departFragment);
        byInstanceId.put(departId, departFragment);
        instanceOwners.put(departId, player.getUniqueId());
        writeInstanceId(departingStack, departId);

        if (remainAmount > 0) {
            ItemStack remainOriginal = original.original().clone();
            remainOriginal.setAmount(remainAmount);
            // the physical remainder left in inventory keeps carrying the
            // id it always had - nothing to rewrite on it, only the
            // tracked amount shrinks.
            LockedItem remainFragment = new LockedItem(originalInstanceId, remainOriginal);
            items.add(remainFragment);
            byInstanceId.put(originalInstanceId, remainFragment);
            instanceOwners.put(originalInstanceId, player.getUniqueId());
        }
        return departId;
    }

    private void writeInstanceId(ItemStack stack, String instanceId) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId);
        stack.setItemMeta(meta);
    }

    /** Public - {@link DisguiseProtectionListener} calls this the moment a
     * PLAYER picks up a still-tracked, dropped disguise item. If the picker
     * is someone OTHER than the tracked owner, dropping it already meant
     * losing it - same as any other dropped item in the game - so rather
     * than leaving an inert, permanently-disguised decoy sitting in the
     * picker's inventory forever (nobody's unlock() would ever find and
     * revert it, since only the OWNER's locked list ever referenced this
     * id), it reveals to the real item right there for the picker, and the
     * owner's tracking is fully released - their own unlock() won't try to
     * hand them a "couldn't find it" replacement on top of that. Does
     * nothing (returns false) if the item isn't tracked, or the picker IS
     * the owner - an owner picking their own dropped item back up already
     * works correctly through the normal in-inventory restore path.
     *
     * @return true if this pickup was a stranger's and got revealed. */
    public boolean revealForOtherPlayer(Player picker, org.bukkit.entity.Item droppedItem) {
        LockedItem item = releaseIfOwnedByStranger(picker, instanceIdOf(droppedItem.getItemStack()));
        if (item == null) {
            return false;
        }
        droppedItem.setItemStack(item.original());
        return true;
    }

    /** Same idea as the overload above, for a disguised item found mid
     * inventory click/drag instead of as a dropped entity - {@link
     * DisguiseProtectionListener} calls this for every item a click/drag
     * touches before doing anything else with it. Covers taking someone
     * else's item out of a SHARED external inventory (a chest, barrel,
     * ...): without this, {@link #reconcile} would only ever check the
     * CLICKER's own tracked items (never finding a match, since the id
     * belongs to a different player), silently leaving an untracked decoy
     * in the clicker's inventory while the real owner still got a fallback
     * replacement at their own unlock. A no-op (returns false) if the item
     * isn't tracked, or the interactor IS the owner - mutates {@code stack}
     * in place (type + meta only, amount untouched) so the caller doesn't
     * need to separately write it back to whatever slot it came from. */
    public boolean revealForOtherPlayer(Player interactor, ItemStack stack) {
        LockedItem item = releaseIfOwnedByStranger(interactor, instanceIdOf(stack));
        if (item == null) {
            return false;
        }
        ItemStack original = item.original();
        stack.setType(original.getType());
        stack.setItemMeta(original.getItemMeta());
        return true;
    }

    /** Shared teardown for both revealForOtherPlayer overloads: if
     * instanceId is tracked and owned by someone OTHER than interactor,
     * fully releases that tracking (byInstanceId/instanceOwners/dropped/
     * external, and the owner's own locked list) and returns the
     * now-orphaned LockedItem for the caller to materialize. Returns null
     * (no-op, nothing released) if untracked or interactor IS the owner. */
    private LockedItem releaseIfOwnedByStranger(Player interactor, String instanceId) {
        if (instanceId == null) {
            return null;
        }
        UUID ownerId = instanceOwners.get(instanceId);
        if (ownerId == null || ownerId.equals(interactor.getUniqueId())) {
            return null;
        }
        LockedItem item = byInstanceId.remove(instanceId);
        instanceOwners.remove(instanceId);
        droppedInstances.remove(instanceId);
        externalInstances.remove(instanceId);
        List<LockedItem> ownerItems = locked.get(ownerId);
        if (ownerItems != null && item != null) {
            ownerItems.remove(item);
        }
        return item;
    }

    /** O(1) lookup by the dropped entity's own UUID (set at drop time)
     * instead of scanning every entity in every loaded world - the item
     * might have moved (water current, hopper, someone nudging it) but its
     * entity id doesn't change just because it's sitting on the ground. */
    private boolean restoreDroppedInstance(LockedItem item) {
        UUID entityId = droppedInstances.remove(item.instanceId());
        if (entityId == null) {
            return false;
        }
        org.bukkit.entity.Entity entity = plugin.getServer().getEntity(entityId);
        if (!(entity instanceof org.bukkit.entity.Item droppedItem) || !droppedItem.isValid()) {
            // already picked up (by anyone) or despawned - not an error,
            // just means the fallback in unlock() gives the real item back
            // without touching wherever the disguise item ended up.
            return false;
        }
        if (!matchesInstance(droppedItem.getItemStack(), item.instanceId())) {
            return false;
        }
        // change it right where it's lying instead of teleporting it into
        // the player's inventory - it's a dropped item sitting in the
        // world, so that's where the "restore" should visibly happen.
        droppedItem.setItemStack(item.original());
        return true;
    }

    /** Searches every slot the item could currently be sitting in (it's
     * free to move within the player's own inventory while locked - see
     * the class javadoc) for the matching instance id, and replaces it
     * with the original wherever it's actually found. */
    private boolean restoreByInstanceId(Player player, PlayerInventory inv, LockedItem locked) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack current = inv.getItem(slot);
            if (matchesInstance(current, locked.instanceId())) {
                inv.setItem(slot, locked.original());
                return true;
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack current = inv.getItem(i);
            if (matchesInstance(current, locked.instanceId())) {
                inv.setItem(i, locked.original());
                return true;
            }
        }
        return false;
    }

    /** Public - {@link DisguiseProtectionListener} calls this a tick after
     * any inventory click/drag that touched a disguised item (once Bukkit
     * has actually applied it - mid-event, nothing's moved yet), instead of
     * pre-computing exactly where the item is going to land. Re-derives
     * tracking for the given player's locked items against physical
     * reality: their own inventory, their cursor, and (if not null) the
     * external inventory that was open for the click - one independently
     * restorable fragment per location actually found, so an auto-split
     * (part of a stack lands in a chest slot, the rest stays behind - one
     * click, both halves sharing Bukkit's copied id) can never let
     * restoring one fragment hand back the pre-split total while the other
     * physical fragment sits forgotten. A location the id genuinely isn't
     * found in at all (dropped, or moved out of both inventories some
     * other way) is left exactly as tracked - {@link #unlock}'s existing
     * dropped/external/fallback chain still covers that later. */
    public void reconcile(Player player, Inventory external) {
        List<LockedItem> items = locked.get(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (LockedItem tracked : new ArrayList<>(items)) {
            List<Fragment> found = new ArrayList<>();
            collectMatches(inv, tracked.instanceId(), found);
            ItemStack cursor = player.getItemOnCursor();
            if (matchesInstance(cursor, tracked.instanceId())) {
                found.add(new Fragment(cursor, () -> player.setItemOnCursor(cursor), cursor.getAmount(), null));
            }
            if (external != null) {
                collectMatches(external, tracked.instanceId(), found);
            }
            if (found.isEmpty()) {
                continue;
            }
            if (found.size() == 1 && found.get(0).amount() == tracked.original().getAmount()) {
                // whole stack, just possibly relocated.
                if (found.get(0).externalHome() != null) {
                    externalInstances.put(tracked.instanceId(), found.get(0).externalHome());
                } else {
                    externalInstances.remove(tracked.instanceId());
                }
                continue;
            }
            // split across more than one physical location (or a single
            // location holding less than the tracked total) - re-derive
            // one independent fragment per location actually found.
            items.remove(tracked);
            byInstanceId.remove(tracked.instanceId());
            instanceOwners.remove(tracked.instanceId());
            externalInstances.remove(tracked.instanceId());
            boolean first = true;
            for (Fragment fragment : found) {
                String fragId = first ? tracked.instanceId() : UUID.randomUUID().toString();
                first = false;
                if (!fragId.equals(instanceIdOf(fragment.stack()))) {
                    writeInstanceId(fragment.stack(), fragId);
                }
                fragment.writeBack().run();
                ItemStack fragOriginal = tracked.original().clone();
                fragOriginal.setAmount(fragment.amount());
                LockedItem fragmentItem = new LockedItem(fragId, fragOriginal);
                items.add(fragmentItem);
                instanceOwners.put(fragId, player.getUniqueId());
                byInstanceId.put(fragId, fragmentItem);
                if (fragment.externalHome() != null) {
                    externalInstances.put(fragId, fragment.externalHome());
                }
            }
        }
    }

    /** Schedules {@link #reconcile} for the next tick - {@link
     * DisguiseProtectionListener} calls this from an event that fires
     * BEFORE Bukkit actually applies the click/drag, so looking at where
     * things ended up has to wait one tick. */
    public void scheduleReconcile(Player player, Inventory external) {
        plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(player, external));
    }

    private void collectMatches(PlayerInventory inv, String instanceId, List<Fragment> out) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = inv.getItem(slot);
            if (matchesInstance(stack, instanceId)) {
                out.add(new Fragment(stack, () -> inv.setItem(slot, stack), stack.getAmount(), null));
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (matchesInstance(stack, instanceId)) {
                int slotIndex = i;
                out.add(new Fragment(stack, () -> inv.setItem(slotIndex, stack), stack.getAmount(), null));
            }
        }
    }

    private void collectMatches(Inventory inv, String instanceId, List<Fragment> out) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (matchesInstance(stack, instanceId)) {
                int slotIndex = i;
                out.add(new Fragment(stack, () -> inv.setItem(slotIndex, stack), stack.getAmount(), inv));
            }
        }
    }

    /** One physical location a tracked instance id was found in during
     * {@link #reconcile} - {@code writeBack} commits whatever mutation
     * (rewriting the id, if this fragment got a fresh one) back to that
     * exact slot/cursor; {@code externalHome} is null for anything in the
     * player's own inventory/cursor. */
    private record Fragment(ItemStack stack, Runnable writeBack, int amount, Inventory externalHome) {
    }

    private boolean matchesInstance(ItemStack item, String instanceId) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        return instanceId.equals(id);
    }

    /** Re-checks a still-tagged player's inventory for anything that should
     * be locked but isn't yet (e.g. picked up mid-fight, or swapped into a
     * slot scanFullInventory watches) - a lighter version of {@link #lock},
     * safe to call repeatedly on an already-locked player. */
    public void refresh(Player player) {
        if (!locked.containsKey(player.getUniqueId())) {
            return;
        }
        lock(player);
    }

    // A/B/A/A cycle (color A is 3x as common as B), matching the datapack's
    // own flash pattern - per-player so different players' fights don't
    // desync each other's phase.
    private final Map<UUID, Integer> flashPhase = new ConcurrentHashMap<>();

    private void tickFlash() {
        if (!config.disguiseArmorFlash() || locked.isEmpty()) {
            return;
        }
        flashTickCounter++;
        if (flashTickCounter % config.disguiseArmorFlashIntervalTicks() != 0) {
            return;
        }
        for (UUID id : locked.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!hasDisguisedArmor(player)) {
                continue;
            }
            int phase = flashPhase.merge(id, 1, (old, one) -> (old + 1) % 4);
            org.bukkit.Color color = phase == 1 ? config.disguiseArmorFlashColorB() : config.disguiseArmorFlashColorA();

            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.5, 0.3,
                    new Particle.DustOptions(color, 1.0f));

            if (config.disguiseArmorRecolor()) {
                recolorDisguisedArmor(player, color);
            }
        }
    }

    /** Opt-in (disguise.armor-recolor, off by default) - recolors the worn
     * disguise armor itself on top of the particle flash, for materials
     * that actually support dyeing (LeatherArmorMeta). Every attempt to
     * make this silent failed - no real vanilla sound is silent by nature -
     * so this plays disguise.armor-equip-sound each time instead of
     * pretending it's free. */
    private void recolorDisguisedArmor(Player player, org.bukkit.Color color) {
        PlayerInventory inv = player.getInventory();
        boolean changed = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack item = inv.getItem(slot);
            if (item == null || !isDisguised(item)) {
                continue;
            }
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
                leatherMeta.setColor(color);
                item.setItemMeta(leatherMeta);
                inv.setItem(slot, item);
                changed = true;
            }
        }
        if (changed) {
            player.playSound(player.getLocation(), config.disguiseArmorEquipSound(), 1.0f, 1.0f);
        }
    }

    /** Checked live against the player's CURRENT equipment, not stored
     * lock-time metadata - since a disguised item is free to move between
     * armor slots (or out of them) while locked, only the live state
     * reliably answers "is anything they're wearing right now disguised". */
    private boolean hasDisguisedArmor(Player player) {
        var equipment = player.getInventory();
        return isDisguised(equipment.getHelmet()) || isDisguised(equipment.getChestplate())
                || isDisguised(equipment.getLeggings()) || isDisguised(equipment.getBoots());
    }

    private void lockIfMatch(Player player, List<LockedItem> items, PlayerInventory inv, ItemLocation loc) {
        ItemStack current = loc.read(inv);
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        if (isDisguised(current)) {
            return;
        }
        if (!matchesDisabledItem(current, loc)) {
            return;
        }
        String instanceId = UUID.randomUUID().toString();
        LockedItem lockedItem = new LockedItem(instanceId, current.clone());
        items.add(lockedItem);
        byInstanceId.put(instanceId, lockedItem);
        instanceOwners.put(instanceId, player.getUniqueId());
        loc.write(inv, buildDisguiseItem(loc, current.getAmount(), instanceId));

        if (loc instanceof ItemLocation.Equipment eq && ARMOR_SUFFIX.containsKey(eq.slot())) {
            warnings.maybeWarnArmor(player);
        } else {
            warnings.maybeWarnInventory(player);
        }

        long overrideMs = config.itemDurationOverrideMs(current.getType());
        if (overrideMs > 0) {
            scheduleIndividualExpiry(player, instanceId, overrideMs);
        }
    }

    /** disabled-items.*-duration-ms lets a specific item type re-enable
     * earlier (or later) than the rest of the player's combat tag - e.g. a
     * firework rocket back in 3s while everything else stays locked for the
     * full 10s. Runs from the moment THIS item got locked, independent of
     * any later retag of the overall combat timer. Fires unconditionally;
     * {@link #restoreSingleItem} is the one that checks whether there's
     * still anything to do (a no-op if the whole player already got
     * unlocked, or this exact item was already individually restored). */
    private void scheduleIndividualExpiry(Player player, String instanceId, long durationMs) {
        long ticks = Math.max(1, durationMs / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> restoreSingleItem(player, instanceId), ticks);
    }

    private void restoreSingleItem(Player player, String instanceId) {
        LockedItem item = byInstanceId.remove(instanceId);
        instanceOwners.remove(instanceId);
        if (item == null) {
            // already gone - full unlock() (release/death/quit) beat this
            // timer to it.
            return;
        }
        List<LockedItem> items = locked.get(player.getUniqueId());
        if (items != null) {
            items.remove(item);
        }
        PlayerInventory inv = player.getInventory();
        if (restoreByInstanceId(player, inv, item)) {
            return;
        }
        if (restoreDroppedInstance(item)) {
            return;
        }
        if (restoreExternalInstance(item)) {
            return;
        }
        Map<Integer, ItemStack> overflow = inv.addItem(item.original());
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Mirror of {@link #restoreDroppedInstance} for an item last seen
     * inside an external (chest/barrel/dispenser/...) inventory via
     * {@link #reconcile} - a plain linear scan of that inventory's
     * contents, bounded by its (small, fixed) size. */
    private boolean restoreExternalInstance(LockedItem item) {
        Inventory inv = externalInstances.remove(item.instanceId());
        if (inv == null) {
            return false;
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (matchesInstance(inv.getItem(i), item.instanceId())) {
                inv.setItem(i, item.original());
                return true;
            }
        }
        return false;
    }

    private boolean matchesDisabledItem(ItemStack item, ItemLocation loc) {
        Material type = item.getType();
        if (loc instanceof ItemLocation.Equipment eq) {
            if (eq.slot() == EquipmentSlot.CHEST && type == Material.ELYTRA) {
                return config.disableElytra();
            }
            if (ARMOR_SUFFIX.containsKey(eq.slot()) && config.disableArmor()) {
                return true;
            }
        }
        if (type == Material.FIREWORK_ROCKET) {
            return config.disableFireworkRocket();
        }
        if (type == Material.WIND_CHARGE) {
            return config.disableWindCharge();
        }
        for (ScdiConfig.CustomItemRule rule : config.customDisabledItems()) {
            if (rule.matches(item)) {
                return true;
            }
        }
        return false;
    }

    private String formattedDisguiseName() {
        StringBuilder name = new StringBuilder();
        name.append(config.disguiseNameColor());
        if (config.disguiseNameBold()) {
            name.append(ChatColor.BOLD);
        }
        if (config.disguiseNameItalic()) {
            name.append(ChatColor.ITALIC);
        }
        name.append(config.disguiseDisplayName());
        return name.toString();
    }

    private ItemStack buildDisguiseItem(ItemLocation loc, int amount, String instanceId) {
        if (loc instanceof ItemLocation.Equipment eq && ARMOR_SUFFIX.containsKey(eq.slot())) {
            return buildDisguiseArmorItem(eq.slot(), amount, instanceId);
        }
        Material material = resolveMaterial(config.disguiseItemKey(), Material.STICK);
        ItemStack disguise = new ItemStack(material, amount);
        ItemMeta meta = disguise.getItemMeta();
        meta.setDisplayName(formattedDisguiseName());
        meta.setEnchantmentGlintOverride(config.disguiseGlint());
        // purely visual (minecraft:item_model) - the item's real type above
        // is what disable it functionally works against, this just changes
        // what it looks like held/in a slot. Paper exposes this directly;
        // the datapack had to fake it with a component-patch relay trick.
        NamespacedKey modelKey = config.disguiseModelKey();
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId);
        disguise.setItemMeta(meta);
        return disguise;
    }

    /** A worn disguise needs to actually BE an armor piece of the right slot
     * to render at all - an arbitrary item (a stick) shows as nothing on the
     * player's body when placed directly in an armor slot. Falls back to a
     * plain stick if the configured material name doesn't resolve to a real
     * armor piece for this slot. Also applies disguise.model on top, for the
     * inventory icon - the WORN appearance is controlled by the real armor
     * material chosen above, item_model doesn't affect that. */
    private ItemStack buildDisguiseArmorItem(EquipmentSlot slot, int amount, String instanceId) {
        String materialName = config.disguiseArmorMaterial().toUpperCase() + ARMOR_SUFFIX.get(slot);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.matchMaterial("LEATHER" + ARMOR_SUFFIX.get(slot));
        }
        ItemStack disguise = new ItemStack(material != null ? material : Material.STICK, 1);
        ItemMeta meta = disguise.getItemMeta();
        meta.setDisplayName(formattedDisguiseName());
        meta.setEnchantmentGlintOverride(config.disguiseGlint());
        NamespacedKey modelKey = config.disguiseModelKey();
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId);
        disguise.setItemMeta(meta);
        return disguise;
    }

    private Material resolveMaterial(NamespacedKey key, Material fallback) {
        if (key == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(key.getKey());
        return material != null ? material : fallback;
    }

    /** Public - used by {@link DisguiseProtectionListener} to detect when a
     * disguised item is involved in a click/drag/drop/placement at all. */
    public boolean isDisguised(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    /** Public - {@link DisguiseProtectionListener} reads this off a dropped
     * item to call {@link #trackDrop}. Null if the item isn't a disguise. */
    public String instanceIdOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
    }

    private record LockedItem(String instanceId, ItemStack original) {
    }
}
