package dev.sacdj.scdi.config;

import java.util.List;

/**
 * The fixed, ordered list of every config.yml key this plugin knows about.
 * ConfigCodec uses this to export/import just the VALUES in this exact
 * order, with no key names in the code at all - both sides run the same
 * plugin version, so the schema doesn't need to travel with the data. Add
 * new settings to the END of this list only; reordering or removing an
 * entry breaks every code anyone's already generated.
 */
public final class ConfigSchema {

    public enum Type { BOOLEAN, LONG, DOUBLE, STRING }

    public record Field(String path, Type type) {
    }

    public static final List<Field> FIELDS = List.of(
            new Field("combat.duration-ms", Type.LONG),
            new Field("combat.tag-attacker", Type.BOOLEAN),
            new Field("combat.tag-victim", Type.BOOLEAN),
            new Field("combat.pve-mode", Type.BOOLEAN),
            new Field("combat.retag-resets-timer", Type.BOOLEAN),
            new Field("combat.reset-on-death", Type.BOOLEAN),
            new Field("combat.ignore-creative", Type.BOOLEAN),
            new Field("combat.ranged-attacks-tag", Type.BOOLEAN),
            new Field("disabled-items.firework-rocket", Type.BOOLEAN),
            new Field("disabled-items.wind-charge", Type.BOOLEAN),
            new Field("disabled-items.elytra", Type.BOOLEAN),
            new Field("disabled-items.scan-full-inventory", Type.BOOLEAN),
            new Field("disguise.item", Type.STRING),
            new Field("disguise.name", Type.STRING),
            new Field("disguise.glint", Type.BOOLEAN),
            new Field("sounds.combat", Type.STRING),
            new Field("sounds.combat-pitch", Type.DOUBLE),
            new Field("sounds.safe", Type.STRING),
            new Field("sounds.safe-pitch", Type.DOUBLE),
            new Field("display.actionbar", Type.BOOLEAN),
            new Field("display.title-on-tag", Type.BOOLEAN),

            // appended after 0.1.0 - see the class comment, append-only from here down.
            // disabled-items.custom-items is a list, not a scalar - deliberately not
            // included in the share-code format.
            new Field("combat.hit-tagging-enabled", Type.BOOLEAN),
            new Field("combat.team-tag-attacker", Type.BOOLEAN),
            new Field("combat.team-tag-victim", Type.BOOLEAN),
            new Field("disabled-items.armor", Type.BOOLEAN),
            new Field("disguise.armor-material", Type.STRING),
            new Field("disguise.armor-flash", Type.BOOLEAN),
            new Field("disguise.armor-flash-interval-ticks", Type.LONG),
            new Field("warnings.armor-warning", Type.BOOLEAN),
            new Field("warnings.armor-warning-sound", Type.BOOLEAN),
            new Field("warnings.armor-warning-sound-id", Type.STRING),
            new Field("warnings.inventory-warning", Type.BOOLEAN),
            new Field("warnings.inventory-warning-sound", Type.BOOLEAN),
            new Field("warnings.inventory-warning-sound-id", Type.STRING),
            new Field("proximity.enabled", Type.BOOLEAN),
            new Field("proximity.distance", Type.DOUBLE),
            new Field("proximity.retag-distance", Type.DOUBLE),
            new Field("proximity.role-by-movement", Type.BOOLEAN),
            new Field("proximity.team-tag-proximity", Type.BOOLEAN),
            new Field("proximity.interval-ticks", Type.LONG),
            new Field("teams.request-timeout-seconds", Type.LONG),
            new Field("one-shot.announce", Type.BOOLEAN),
            new Field("one-shot.ignore-tag", Type.BOOLEAN),
            new Field("one-shot.cooldown-enabled", Type.BOOLEAN),
            new Field("one-shot.cooldown-seconds", Type.LONG),
            new Field("one-shot.no-tag-attacker-on-kill", Type.BOOLEAN),
            new Field("one-shot.no-tag-victim-on-kill", Type.BOOLEAN),
            new Field("dummy.allow-player-spawn", Type.BOOLEAN),
            new Field("dummy.spawn-cooldown-seconds", Type.LONG),
            new Field("dummy.max-per-player", Type.LONG),
            new Field("dummy.max-total", Type.LONG),
            new Field("dummy.max-health", Type.DOUBLE),
            new Field("dummy.one-shot-damage", Type.DOUBLE),
            new Field("dummy.invincible-default", Type.BOOLEAN),
            new Field("dummy.immobile", Type.BOOLEAN),
            new Field("dummy.look-at-player", Type.BOOLEAN),
            new Field("dummy.look-range", Type.DOUBLE),
            new Field("dummy.regen-enabled", Type.BOOLEAN),
            new Field("dummy.regen-delay-seconds", Type.LONG),
            new Field("dummy.regen-amount", Type.DOUBLE),
            new Field("dummy.regen-interval-ticks", Type.LONG),
            new Field("dummy.show-health", Type.BOOLEAN),
            new Field("dummy.damage-numbers", Type.BOOLEAN),
            new Field("dummy.announce-one-shot", Type.BOOLEAN),
            new Field("dummy.announce-time-to-kill", Type.BOOLEAN),
            new Field("dummy.pickup-items", Type.BOOLEAN),
            new Field("display.show-timer-above-head", Type.BOOLEAN),
            new Field("disguise.model", Type.STRING),

            // appended for the datapack-parity pass (disguise cosmetics,
            // per-item durations, dummy cheat-death/pin/gravity, sound
            // volumes, tab team display) - see the class comment.
            new Field("disguise.name-color", Type.STRING),
            new Field("disguise.name-bold", Type.BOOLEAN),
            new Field("disguise.name-italic", Type.BOOLEAN),
            new Field("disguise.armor-flash-color-a", Type.LONG),
            new Field("disguise.armor-flash-color-b", Type.LONG),
            new Field("disguise.armor-recolor", Type.BOOLEAN),
            new Field("disguise.armor-equip-sound", Type.STRING),
            new Field("disabled-items.firework-rocket-duration-ms", Type.LONG),
            new Field("disabled-items.wind-charge-duration-ms", Type.LONG),
            new Field("disabled-items.elytra-duration-ms", Type.LONG),
            new Field("dummy.pinned-default", Type.BOOLEAN),
            new Field("dummy.no-gravity", Type.BOOLEAN),
            new Field("dummy.announce-range", Type.DOUBLE),
            new Field("dummy.announce-cheated-death", Type.BOOLEAN),
            new Field("dummy.extinguish-in-combat", Type.BOOLEAN),
            new Field("dummy.extinguish-on-cheat-death", Type.BOOLEAN),
            new Field("dummy.cheat-death-invulnerability", Type.BOOLEAN),
            new Field("dummy.cheat-death-invulnerability-ticks", Type.LONG),
            new Field("dummy.cheat-death-sound-totem", Type.BOOLEAN),
            new Field("dummy.cheat-death-sound-allay", Type.BOOLEAN),
            new Field("dummy.cheat-death-particle", Type.STRING),
            new Field("dummy.dps-window-ticks", Type.LONG),
            new Field("sounds.combat-volume", Type.DOUBLE),
            new Field("sounds.safe-volume", Type.DOUBLE),
            new Field("display.show-team-on-tab", Type.BOOLEAN)
    );

    private ConfigSchema() {
    }
}
