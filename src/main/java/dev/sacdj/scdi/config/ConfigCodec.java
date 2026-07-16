package dev.sacdj.scdi.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Turns the config into a single shareable text code (and back) - "give this
 * string to a friend and they get your exact settings" instead of them
 * copying a file around.
 *
 * <p>Deliberately does NOT serialize the YAML itself. A full YAML dump
 * repeats every key's full dotted path (e.g. "disabled-items.scan-full-
 * inventory") for every single setting, which is pure overhead once you
 * consider that both sides always run the same plugin version and therefore
 * already agree on {@link ConfigSchema} - so this only encodes the VALUES,
 * in that fixed known order, with a 1-char type tag per value instead of a
 * key name. That's the majority of the size difference between this and the
 * naive "compress the whole config.yml" version this replaced.
 *
 * <p>Format (before compression): for each field in schema order, one type
 * tag char ('B'/'L'/'D'/'S') followed by its value, separated by
 * {@link #FIELD_SEP} (a control character no legitimate config value would
 * ever contain). Then Deflate + base64url. The "SCDI2:" prefix rejects
 * garbage input with a clear error and lets the format change again later
 * without silently misreading an old code - a bare "SCDI1:" full-YAML code
 * from an earlier build is explicitly detected and reported as unsupported,
 * not silently mis-parsed.
 */
public final class ConfigCodec {

    private static final String PREFIX = "SCDI2:";
    private static final String OLD_PREFIX = "SCDI1:";
    private static final char FIELD_SEP = '\u001F';

    private final JavaPlugin plugin;

    public ConfigCodec(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String export() {
        StringBuilder sb = new StringBuilder();
        for (ConfigSchema.Field field : ConfigSchema.FIELDS) {
            sb.append(tagFor(field.type()));
            switch (field.type()) {
                case BOOLEAN -> sb.append(plugin.getConfig().getBoolean(field.path()) ? '1' : '0');
                case LONG -> sb.append(plugin.getConfig().getLong(field.path()));
                case DOUBLE -> sb.append(plugin.getConfig().getDouble(field.path()));
                case STRING -> sb.append(sanitize(plugin.getConfig().getString(field.path(), "")));
            }
            sb.append(FIELD_SEP);
        }

        byte[] raw = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream out = new DeflaterOutputStream(compressed, deflater)) {
            out.write(raw);
        } catch (IOException e) {
            throw new IllegalStateException("failed to compress config for export", e);
        } finally {
            deflater.end();
        }

        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed.toByteArray());
    }

    /**
     * @return null on success, or a human-readable reason it failed.
     */
    public String importCode(String code) {
        String trimmed = code.trim();
        if (trimmed.startsWith(OLD_PREFIX)) {
            return "That's a code from an older plugin version and can't be imported here - ask your friend to re-export with the current version.";
        }
        if (!trimmed.startsWith(PREFIX)) {
            return "That doesn't look like a Combat Disabled Items config code (missing/wrong prefix).";
        }

        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return "That code is corrupted (not valid base64).";
        }

        String decoded;
        ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            in.transferTo(decompressed);
            decoded = decompressed.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "That code is corrupted (failed to decompress).";
        }

        String[] tokens = decoded.split(String.valueOf(FIELD_SEP), -1);
        if (tokens.length < ConfigSchema.FIELDS.size()) {
            return "That code doesn't have enough fields for this plugin's config schema - probably from an incompatible version.";
        }

        // validate every field BEFORE applying any of them, so a corrupt
        // code can never leave the config half-updated.
        for (int i = 0; i < ConfigSchema.FIELDS.size(); i++) {
            ConfigSchema.Field field = ConfigSchema.FIELDS.get(i);
            String token = tokens[i];
            if (token.isEmpty() || token.charAt(0) != tagFor(field.type())) {
                return "That code is corrupted (type mismatch for " + field.path() + ").";
            }
            String value = token.substring(1);
            try {
                switch (field.type()) {
                    case LONG -> Long.parseLong(value);
                    case DOUBLE -> Double.parseDouble(value);
                    case BOOLEAN, STRING -> {
                        // any string is valid for these two
                    }
                }
            } catch (NumberFormatException e) {
                return "That code is corrupted (bad value for " + field.path() + ").";
            }
        }

        for (int i = 0; i < ConfigSchema.FIELDS.size(); i++) {
            ConfigSchema.Field field = ConfigSchema.FIELDS.get(i);
            String value = tokens[i].substring(1);
            switch (field.type()) {
                case BOOLEAN -> plugin.getConfig().set(field.path(), "1".equals(value));
                case LONG -> plugin.getConfig().set(field.path(), Long.parseLong(value));
                case DOUBLE -> plugin.getConfig().set(field.path(), Double.parseDouble(value));
                case STRING -> plugin.getConfig().set(field.path(), value);
            }
        }

        plugin.saveConfig();
        return null;
    }

    private static char tagFor(ConfigSchema.Type type) {
        return switch (type) {
            case BOOLEAN -> 'B';
            case LONG -> 'L';
            case DOUBLE -> 'D';
            case STRING -> 'S';
        };
    }

    /** Strips the field separator out of string values before encoding - the
     * only character that would otherwise break the format, and not
     * something any legitimate sound id/item id/display name needs. */
    private static String sanitize(String value) {
        return value.replace(FIELD_SEP, ' ');
    }
}
