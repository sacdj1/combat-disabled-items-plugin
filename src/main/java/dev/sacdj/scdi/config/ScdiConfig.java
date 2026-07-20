package dev.sacdj.scdi.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin typed wrapper over config.yml. Reload-safe: {@link #reload()} re-reads
 * from disk so /scdi reload doesn't need a server restart.
 *
 * <p>{@code saveDefaultConfig()} alone only writes config.yml the FIRST time
 * it doesn't exist - a plugin update that adds new keys to the bundled
 * default never touches an existing installed config.yml on disk at all, so
 * every new setting would silently read back as Bukkit's built-in zero
 * value (false/0/null) instead of its intended default until the key is
 * added by hand. {@link #mergeMissingDefaults()} does the datapack's
 * equivalent of "execute unless data storage ... run data modify ... set
 * value <default>" - additively fills in anything missing from the bundled
 * default, in place, without touching keys the installer already changed.
 */
public final class ScdiConfig {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public ScdiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.cfg = plugin.getConfig();
        mergeMissingDefaults();
    }

    private void mergeMissingDefaults() {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : bundled.getKeys(true)) {
                if (bundled.isConfigurationSection(key)) {
                    continue;
                }
                if (!cfg.contains(key)) {
                    cfg.set(key, bundled.get(key));
                    changed = true;
                }
            }
            if (changed) {
                plugin.saveConfig();
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Couldn't merge new config defaults: " + e.getMessage());
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
        mergeMissingDefaults();
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

    // ---- combat ----

    public Duration combatDuration() {
        return Duration.ofMillis(cfg.getLong("combat.duration-ms", 10000));
    }

    public boolean hitTaggingEnabled() {
        return cfg.getBoolean("combat.hit-tagging-enabled", true);
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

    public boolean teamTagAttacker() {
        return cfg.getBoolean("combat.team-tag-attacker", true);
    }

    public boolean teamTagVictim() {
        return cfg.getBoolean("combat.team-tag-victim", false);
    }

    // ---- disabled-items ----

    public boolean disableFireworkRocket() {
        return cfg.getBoolean("disabled-items.firework-rocket", true);
    }

    public boolean disableWindCharge() {
        return cfg.getBoolean("disabled-items.wind-charge", false);
    }

    public boolean disableElytra() {
        return cfg.getBoolean("disabled-items.elytra", false);
    }

    public boolean disableArmor() {
        return cfg.getBoolean("disabled-items.armor", false);
    }

    public boolean scanFullInventory() {
        return cfg.getBoolean("disabled-items.scan-full-inventory", true);
    }

    /** 0 means "use combat.duration-ms" (the global timer). */
    public long fireworkRocketDurationMs() {
        return cfg.getLong("disabled-items.firework-rocket-duration-ms", 0);
    }

    public long windChargeDurationMs() {
        return cfg.getLong("disabled-items.wind-charge-duration-ms", 0);
    }

    public long elytraDurationMs() {
        return cfg.getLong("disabled-items.elytra-duration-ms", 0);
    }

    /** 0 means "no override, use combat.duration-ms" - the only materials
     * with a configurable override are the three built-ins above, since
     * custom-items rules don't carry their own duration field. */
    public long itemDurationOverrideMs(Material type) {
        if (type == Material.FIREWORK_ROCKET) {
            return fireworkRocketDurationMs();
        }
        if (type == Material.WIND_CHARGE) {
            return windChargeDurationMs();
        }
        if (type == Material.ELYTRA) {
            return elytraDurationMs();
        }
        return 0;
    }

    /** Extra held items to disable, beyond the built-ins above - each entry
     * is {material: "ENDER_PEARL"} under disabled-items.custom-items. */
    /** A rule matches an item if EVERY non-null field matches - material
     * alone ("any diamond sword"), enchantment alone ("anything with
     * Sharpness, regardless of type"), or both together ("specifically a
     * bow enchanted with Power", distinct from disabling all bows). */
    public record CustomItemRule(Material material, Enchantment enchantment) {
        public boolean matches(ItemStack item) {
            if (material != null && item.getType() != material) {
                return false;
            }
            if (enchantment != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasEnchant(enchantment)) {
                    return false;
                }
            }
            return material != null || enchantment != null;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            if (material != null) {
                map.put("material", material.name());
            }
            if (enchantment != null) {
                map.put("enchantment", enchantment.getKey().getKey());
            }
            return map;
        }
    }

    public List<CustomItemRule> customDisabledItems() {
        List<Map<?, ?>> entries = cfg.getMapList("disabled-items.custom-items");
        List<CustomItemRule> rules = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            Object rawMaterial = entry.get("material");
            Material material = rawMaterial == null ? null : Material.matchMaterial(String.valueOf(rawMaterial));
            Object rawEnchant = entry.get("enchantment");
            Enchantment enchantment = rawEnchant == null ? null : resolveEnchantment(String.valueOf(rawEnchant));
            if (material == null && enchantment == null) {
                continue;
            }
            rules.add(new CustomItemRule(material, enchantment));
        }
        return rules;
    }

    public void addCustomDisabledItem(Material material, Enchantment enchantment) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (CustomItemRule existing : customDisabledItems()) {
            entries.add(existing.toMap());
        }
        entries.add(new CustomItemRule(material, enchantment).toMap());
        set("disabled-items.custom-items", entries);
    }

    public void removeCustomDisabledItem(int index) {
        List<CustomItemRule> existing = customDisabledItems();
        if (index < 0 || index >= existing.size()) {
            return;
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < existing.size(); i++) {
            if (i != index) {
                entries.add(existing.get(i).toMap());
            }
        }
        set("disabled-items.custom-items", entries);
    }

    private static Enchantment resolveEnchantment(String key) {
        String path = key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
        return org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(path.toLowerCase(java.util.Locale.ROOT)));
    }

    // ---- disguise ----

    public NamespacedKey disguiseItemKey() {
        String id = cfg.getString("disguise.item", "minecraft:stick");
        return NamespacedKey.fromString(id.replace("minecraft:", ""));
    }

    /** Purely visual (minecraft:item_model) - null means don't override,
     * just show whatever disguiseItemKey()'s real material looks like. */
    public NamespacedKey disguiseModelKey() {
        String id = cfg.getString("disguise.model", "minecraft:barrier");
        if (id == null || id.isBlank()) {
            return null;
        }
        return NamespacedKey.fromString(id.replace("minecraft:", ""));
    }

    public String disguiseDisplayName() {
        return cfg.getString("disguise.name", "Items Disabled!");
    }

    public ChatColor disguiseNameColor() {
        try {
            return ChatColor.valueOf(cfg.getString("disguise.name-color", "RED").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ChatColor.RED;
        }
    }

    public boolean disguiseNameBold() {
        return cfg.getBoolean("disguise.name-bold", true);
    }

    public boolean disguiseNameItalic() {
        return cfg.getBoolean("disguise.name-italic", false);
    }

    public boolean disguiseGlint() {
        return cfg.getBoolean("disguise.glint", true);
    }

    public String disguiseArmorMaterial() {
        return cfg.getString("disguise.armor-material", "LEATHER");
    }

    public boolean disguiseArmorFlash() {
        return cfg.getBoolean("disguise.armor-flash", true);
    }

    public long disguiseArmorFlashIntervalTicks() {
        return cfg.getLong("disguise.armor-flash-interval-ticks", 6);
    }

    /** Packed 24-bit RGB, same representation minecraft:dyed_color uses. */
    public org.bukkit.Color disguiseArmorFlashColorA() {
        return org.bukkit.Color.fromRGB(cfg.getInt("disguise.armor-flash-color-a", 16711680));
    }

    public org.bukkit.Color disguiseArmorFlashColorB() {
        return org.bukkit.Color.fromRGB(cfg.getInt("disguise.armor-flash-color-b", 16776960));
    }

    public boolean disguiseArmorRecolor() {
        return cfg.getBoolean("disguise.armor-recolor", false);
    }

    public Sound disguiseArmorEquipSound() {
        return soundOrNull(cfg.getString("disguise.armor-equip-sound", "block.candle.extinguish"));
    }

    // ---- warnings ----

    public boolean armorWarningDefault() {
        return cfg.getBoolean("warnings.armor-warning", true);
    }

    public boolean armorWarningSoundDefault() {
        return cfg.getBoolean("warnings.armor-warning-sound", false);
    }

    public Sound armorWarningSound() {
        return soundOrNull(cfg.getString("warnings.armor-warning-sound-id", "block.note_block.bit"));
    }

    public boolean inventoryWarningDefault() {
        return cfg.getBoolean("warnings.inventory-warning", false);
    }

    public boolean inventoryWarningSoundDefault() {
        return cfg.getBoolean("warnings.inventory-warning-sound", false);
    }

    public Sound inventoryWarningSound() {
        return soundOrNull(cfg.getString("warnings.inventory-warning-sound-id", "block.note_block.bit"));
    }

    // ---- proximity ----

    public boolean proximityEnabled() {
        return cfg.getBoolean("proximity.enabled", false);
    }

    public double proximityDistance() {
        return cfg.getDouble("proximity.distance", 6.0);
    }

    public double proximityRetagDistance() {
        return cfg.getDouble("proximity.retag-distance", 6.0);
    }

    public boolean proximityRoleByMovement() {
        return cfg.getBoolean("proximity.role-by-movement", false);
    }

    public boolean teamTagProximity() {
        return cfg.getBoolean("proximity.team-tag-proximity", false);
    }

    public long proximityIntervalTicks() {
        return Math.max(1, cfg.getLong("proximity.interval-ticks", 5));
    }

    // ---- teams ----

    public long teamRequestTimeoutSeconds() {
        return cfg.getLong("teams.request-timeout-seconds", 30);
    }

    // ---- one-shot ----

    public boolean oneShotAnnounce() {
        return cfg.getBoolean("one-shot.announce", true);
    }

    public boolean oneShotIgnoreTag() {
        return cfg.getBoolean("one-shot.ignore-tag", false);
    }

    public boolean oneShotCooldownEnabled() {
        return cfg.getBoolean("one-shot.cooldown-enabled", false);
    }

    public long oneShotCooldownSeconds() {
        return cfg.getLong("one-shot.cooldown-seconds", 10);
    }

    public boolean oneShotNoTagAttacker() {
        return cfg.getBoolean("one-shot.no-tag-attacker-on-kill", false);
    }

    public boolean oneShotNoTagVictim() {
        return cfg.getBoolean("one-shot.no-tag-victim-on-kill", true);
    }

    // ---- dummy ----

    public boolean dummyAllowPlayerSpawn() {
        return cfg.getBoolean("dummy.allow-player-spawn", false);
    }

    public long dummySpawnCooldownSeconds() {
        return cfg.getLong("dummy.spawn-cooldown-seconds", 30);
    }

    public int dummyMaxPerPlayer() {
        return cfg.getInt("dummy.max-per-player", 2);
    }

    public int dummyMaxTotal() {
        return cfg.getInt("dummy.max-total", 10);
    }

    public double dummyMaxHealth() {
        return cfg.getDouble("dummy.max-health", 1000.0);
    }

    public double dummyOneShotDamage() {
        return cfg.getDouble("dummy.one-shot-damage", 20.0);
    }

    public boolean dummyInvincibleDefault() {
        return cfg.getBoolean("dummy.invincible-default", false);
    }

    public boolean dummyImmobile() {
        return cfg.getBoolean("dummy.immobile", true);
    }

    public boolean dummyPinnedDefault() {
        return cfg.getBoolean("dummy.pinned-default", false);
    }

    public boolean dummyNoGravity() {
        return cfg.getBoolean("dummy.no-gravity", false);
    }

    public boolean dummyLookAtPlayer() {
        return cfg.getBoolean("dummy.look-at-player", true);
    }

    public double dummyLookRange() {
        return cfg.getDouble("dummy.look-range", 5.0);
    }

    public boolean dummyRegenEnabled() {
        return cfg.getBoolean("dummy.regen-enabled", true);
    }

    public long dummyRegenDelaySeconds() {
        return cfg.getLong("dummy.regen-delay-seconds", 5);
    }

    public double dummyRegenAmount() {
        return cfg.getDouble("dummy.regen-amount", 1.0);
    }

    public long dummyRegenIntervalTicks() {
        return Math.max(1, cfg.getLong("dummy.regen-interval-ticks", 20));
    }

    public boolean dummyShowHealth() {
        return cfg.getBoolean("dummy.show-health", true);
    }

    public boolean dummyDamageNumbers() {
        return cfg.getBoolean("dummy.damage-numbers", true);
    }

    public boolean dummyAnnounceOneShot() {
        return cfg.getBoolean("dummy.announce-one-shot", true);
    }

    public boolean dummyAnnounceTimeToKill() {
        return cfg.getBoolean("dummy.announce-time-to-kill", true);
    }

    public boolean dummyPickupItems() {
        return cfg.getBoolean("dummy.pickup-items", false);
    }

    public double dummyAnnounceRange() {
        return cfg.getDouble("dummy.announce-range", 24.0);
    }

    public boolean dummyAnnounceCheatedDeath() {
        return cfg.getBoolean("dummy.announce-cheated-death", false);
    }

    public boolean dummyExtinguishInCombat() {
        return cfg.getBoolean("dummy.extinguish-in-combat", false);
    }

    public boolean dummyExtinguishOnCheatDeath() {
        return cfg.getBoolean("dummy.extinguish-on-cheat-death", true);
    }

    public boolean dummyCheatDeathInvulnerability() {
        return cfg.getBoolean("dummy.cheat-death-invulnerability", false);
    }

    public long dummyCheatDeathInvulnerabilityTicks() {
        return cfg.getLong("dummy.cheat-death-invulnerability-ticks", 20);
    }

    public boolean dummyCheatDeathSoundTotem() {
        return cfg.getBoolean("dummy.cheat-death-sound-totem", true);
    }

    public boolean dummyCheatDeathSoundAllay() {
        return cfg.getBoolean("dummy.cheat-death-sound-allay", true);
    }

    public Particle dummyCheatDeathParticle() {
        String id = cfg.getString("dummy.cheat-death-particle", "minecraft:electric_spark");
        String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        try {
            return Particle.valueOf(path.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.ELECTRIC_SPARK;
        }
    }

    public long dummyDpsWindowTicks() {
        return Math.max(1, cfg.getLong("dummy.dps-window-ticks", 40));
    }

    // ---- sounds ----

    public Sound combatSound() {
        return soundOrNull(cfg.getString("sounds.combat", "block.note_block.bit"));
    }

    public float combatPitch() {
        return (float) cfg.getDouble("sounds.combat-pitch", 0.5);
    }

    public float combatVolume() {
        return (float) cfg.getDouble("sounds.combat-volume", 1.0);
    }

    public Sound safeSound() {
        return soundOrNull(cfg.getString("sounds.safe", "block.note_block.bit"));
    }

    public float safePitch() {
        return (float) cfg.getDouble("sounds.safe-pitch", 1.0);
    }

    public float safeVolume() {
        return (float) cfg.getDouble("sounds.safe-volume", 1.0);
    }

    // ---- display ----

    public boolean showActionBar() {
        return cfg.getBoolean("display.actionbar", true);
    }

    public boolean showDisabledText() {
        return cfg.getBoolean("display.show-disabled-text", true);
    }

    public boolean showTitleOnTag() {
        return cfg.getBoolean("display.title-on-tag", true);
    }

    public boolean showTimerAboveHead() {
        return cfg.getBoolean("display.show-timer-above-head", false);
    }

    public boolean showTeamOnTab() {
        return cfg.getBoolean("display.show-team-on-tab", false);
    }

    private static Sound soundOrNull(String key) {
        String path = key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
        Sound sound = org.bukkit.Registry.SOUNDS.get(NamespacedKey.minecraft(path));
        return sound != null ? sound : Sound.BLOCK_NOTE_BLOCK_BIT;
    }
}
