package dev.sacdj.scdi.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Thin typed wrapper over config.yml. Reload-safe: {@link #reload()} re-reads
 * from disk so /scdi reload doesn't need a server restart.
 */
public final class ScdiConfig {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public ScdiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    /** Raw dot-path access for the GUI/command config editors - both work
     * directly against the same keys used in config.yml, no separate schema
     * to keep in sync. */
    public FileConfiguration raw() {
        return cfg;
    }

    public void set(String path, Object value) {
        cfg.set(path, value);
        plugin.saveConfig();
    }

    public Duration combatDuration() {
        return Duration.ofMillis(cfg.getLong("combat.duration-ms", 10000));
    }

    public boolean tagAttacker() {
        return cfg.getBoolean("combat.tag-attacker", true);
    }

    public boolean tagVictim() {
        return cfg.getBoolean("combat.tag-victim", true);
    }

    public boolean pveMode() {
        return cfg.getBoolean("combat.pve-mode", false);
    }

    public boolean retagResetsTimer() {
        return cfg.getBoolean("combat.retag-resets-timer", true);
    }

    public boolean resetOnDeath() {
        return cfg.getBoolean("combat.reset-on-death", false);
    }

    public boolean ignoreCreative() {
        return cfg.getBoolean("combat.ignore-creative", false);
    }

    public boolean rangedAttacksTag() {
        return cfg.getBoolean("combat.ranged-attacks-tag", true);
    }

    public boolean disableFireworkRocket() {
        return cfg.getBoolean("disabled-items.firework-rocket", true);
    }

    public boolean disableWindCharge() {
        return cfg.getBoolean("disabled-items.wind-charge", false);
    }

    public boolean disableElytra() {
        return cfg.getBoolean("disabled-items.elytra", false);
    }

    public NamespacedKey disguiseItemKey() {
        String id = cfg.getString("disguise.item", "minecraft:stick");
        return NamespacedKey.fromString(id.replace("minecraft:", ""));
    }

    public String disguiseDisplayName() {
        return cfg.getString("disguise.name", "Items Disabled!");
    }

    public boolean disguiseGlint() {
        return cfg.getBoolean("disguise.glint", true);
    }

    public Sound combatSound() {
        return soundOrNull(cfg.getString("sounds.combat", "block.note_block.bit"));
    }

    public float combatPitch() {
        return (float) cfg.getDouble("sounds.combat-pitch", 0.5);
    }

    public Sound safeSound() {
        return soundOrNull(cfg.getString("sounds.safe", "block.note_block.bit"));
    }

    public float safePitch() {
        return (float) cfg.getDouble("sounds.safe-pitch", 1.0);
    }

    public boolean showActionBar() {
        return cfg.getBoolean("display.actionbar", true);
    }

    public boolean showTitleOnTag() {
        return cfg.getBoolean("display.title-on-tag", true);
    }

    public boolean scanFullInventory() {
        return cfg.getBoolean("disabled-items.scan-full-inventory", true);
    }

    private static Sound soundOrNull(String key) {
        String path = key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
        Sound sound = org.bukkit.Registry.SOUNDS.get(NamespacedKey.minecraft(path));
        return sound != null ? sound : Sound.BLOCK_NOTE_BLOCK_BIT;
    }
}
