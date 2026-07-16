package dev.sacdj.scdi.warning;

import dev.sacdj.scdi.config.ScdiConfig;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Once-per-encounter "your armor/inventory items were disabled" chat
 * warnings, each with an independent per-player preference (on/off, plus a
 * separate on/off for whether it also plays a sound) - stored directly on
 * the player via {@link org.bukkit.persistence.PersistentDataContainer}
 * rather than a separate player-data file, and lazily defaulted from the
 * config the first time it's ever read for a player who hasn't set their
 * own preference (mirrors the datapack's scdi_armor_warning_pref).
 */
public final class WarningManager {

    private final ScdiConfig config;

    private final NamespacedKey armorWarnKey;
    private final NamespacedKey armorSoundKey;
    private final NamespacedKey invWarnKey;
    private final NamespacedKey invSoundKey;

    private final Set<UUID> armorWarnedThisEncounter = ConcurrentHashMap.newKeySet();
    private final Set<UUID> invWarnedThisEncounter = ConcurrentHashMap.newKeySet();

    public WarningManager(JavaPlugin plugin, ScdiConfig config) {
        this.config = config;
        this.armorWarnKey = new NamespacedKey(plugin, "armor_warning_pref");
        this.armorSoundKey = new NamespacedKey(plugin, "armor_warning_sound_pref");
        this.invWarnKey = new NamespacedKey(plugin, "inventory_warning_pref");
        this.invSoundKey = new NamespacedKey(plugin, "inventory_warning_sound_pref");
    }

    public void onCombatEnd(Player player) {
        armorWarnedThisEncounter.remove(player.getUniqueId());
        invWarnedThisEncounter.remove(player.getUniqueId());
    }

    public void maybeWarnArmor(Player player) {
        if (!armorWarnedThisEncounter.add(player.getUniqueId())) {
            return;
        }
        if (!getPref(player, armorWarnKey, config.armorWarningDefault())) {
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Your armor has been disabled while you're in combat.");
        if (getPref(player, armorSoundKey, config.armorWarningSoundDefault())) {
            player.playSound(player.getLocation(), config.armorWarningSound(), 1.0f, 1.0f);
        }
    }

    public void maybeWarnInventory(Player player) {
        if (!invWarnedThisEncounter.add(player.getUniqueId())) {
            return;
        }
        if (!getPref(player, invWarnKey, config.inventoryWarningDefault())) {
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "An item in your inventory has been disabled while you're in combat.");
        if (getPref(player, invSoundKey, config.inventoryWarningSoundDefault())) {
            player.playSound(player.getLocation(), config.inventoryWarningSound(), 1.0f, 1.0f);
        }
    }

    public boolean armorWarningPref(Player player) {
        return getPref(player, armorWarnKey, config.armorWarningDefault());
    }

    public void setArmorWarningPref(Player player, boolean value) {
        setPref(player, armorWarnKey, value);
    }

    public boolean armorWarningSoundPref(Player player) {
        return getPref(player, armorSoundKey, config.armorWarningSoundDefault());
    }

    public void setArmorWarningSoundPref(Player player, boolean value) {
        setPref(player, armorSoundKey, value);
    }

    public boolean inventoryWarningPref(Player player) {
        return getPref(player, invWarnKey, config.inventoryWarningDefault());
    }

    public void setInventoryWarningPref(Player player, boolean value) {
        setPref(player, invWarnKey, value);
    }

    public boolean inventoryWarningSoundPref(Player player) {
        return getPref(player, invSoundKey, config.inventoryWarningSoundDefault());
    }

    public void setInventoryWarningSoundPref(Player player, boolean value) {
        setPref(player, invSoundKey, value);
    }

    private boolean getPref(Player player, NamespacedKey key, boolean fallback) {
        Byte stored = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return stored != null ? stored != 0 : fallback;
    }

    private void setPref(Player player, NamespacedKey key, boolean value) {
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }
}
