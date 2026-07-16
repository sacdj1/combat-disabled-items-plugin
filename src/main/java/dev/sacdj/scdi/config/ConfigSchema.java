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
            new Field("display.title-on-tag", Type.BOOLEAN)
    );

    private ConfigSchema() {
    }
}
