package dev.sacdj.scdi.command;

import dev.sacdj.scdi.combat.CombatManager;
import dev.sacdj.scdi.config.ConfigCodec;
import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.dummy.DummyManager;
import dev.sacdj.scdi.menu.MainMenu;
import dev.sacdj.scdi.menu.MenuManager;
import dev.sacdj.scdi.team.TeamManager;
import dev.sacdj.scdi.util.ChatInputManager;
import dev.sacdj.scdi.warning.WarningManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Mannequin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ScdiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("menu", "reload", "status", "config", "export", "import", "team", "dummy", "settings", "customitem", "debug");
    private static final List<String> DEBUG_SUBCOMMANDS = List.of("tag", "untag");
    private static final List<String> CONFIG_SUBCOMMANDS = List.of("get", "set", "list");
    private static final List<String> TEAM_SUBCOMMANDS = List.of("request", "confirm", "reset");
    private static final List<String> DUMMY_SUBCOMMANDS = List.of("spawn", "remove", "removeall", "invincible", "mortal");
    private static final List<String> SETTINGS_SUBCOMMANDS =
            List.of("armor-warning", "armor-warning-sound", "inventory-warning", "inventory-warning-sound");
    private static final List<String> CUSTOMITEM_SUBCOMMANDS = List.of("add", "remove", "list");

    private final ScdiConfig config;
    private final CombatManager combat;
    private final MenuManager menuManager;
    private final ChatInputManager chatInput;
    private final ConfigCodec codec;
    private final TeamManager teams;
    private final DummyManager dummies;
    private final WarningManager warnings;

    public ScdiCommand(ScdiConfig config, CombatManager combat, MenuManager menuManager,
                        ChatInputManager chatInput, ConfigCodec codec, TeamManager teams,
                        DummyManager dummies, WarningManager warnings) {
        this.config = config;
        this.combat = combat;
        this.menuManager = menuManager;
        this.chatInput = chatInput;
        this.codec = codec;
        this.teams = teams;
        this.dummies = dummies;
        this.warnings = warnings;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player && player.hasPermission("scdi.admin")) {
                menuManager.open(player, new MainMenu(config, chatInput));
            } else {
                sender.sendMessage(ChatColor.GRAY
                        + "/scdi status | team <request|confirm|reset> | dummy <spawn|remove> | settings"
                        + (sender.hasPermission("scdi.admin")
                        ? " | menu | reload | config <get|set|list> | export | import <code>" : ""));
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                if (sender instanceof Player player) {
                    menuManager.open(player, new MainMenu(config, chatInput));
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can open the menu.");
                }
            }
            case "reload" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                config.reload();
                sender.sendMessage(ChatColor.GREEN + "Combat Disabled Items config reloaded.");
            }
            case "status" -> handleStatus(sender);
            case "config" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                handleConfig(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "export" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                String code = codec.export();
                sender.sendMessage(ChatColor.GREEN + "Config code (click to copy, then send it to a friend):");
                if (sender instanceof Player player) {
                    TextComponent codeComponent = new TextComponent(
                            new ComponentBuilder(code).color(ChatColor.YELLOW).create());
                    codeComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code));
                    codeComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text(new ComponentBuilder("Click to copy").color(ChatColor.GRAY).create())));
                    player.spigot().sendMessage(codeComponent);
                } else {
                    sender.sendMessage(ChatColor.YELLOW + code);
                }
            }
            case "import" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scdi import <code>");
                    break;
                }
                String error = codec.importCode(args[1]);
                if (error != null) {
                    sender.sendMessage(ChatColor.RED + error);
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Config imported and applied.");
                }
            }
            case "team" -> handleTeam(sender, Arrays.copyOfRange(args, 1, args.length));
            case "dummy" -> handleDummy(sender, Arrays.copyOfRange(args, 1, args.length));
            case "settings" -> handleSettings(sender, Arrays.copyOfRange(args, 1, args.length));
            case "customitem" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                handleCustomItem(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "debug" -> {
                if (!requireAdmin(sender)) {
                    break;
                }
                handleDebug(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return matches(SUBCOMMANDS, args[0]);
        }

        if (args[0].equalsIgnoreCase("config")) {
            if (args.length == 2) {
                return matches(CONFIG_SUBCOMMANDS, args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("get") || args[1].equalsIgnoreCase("set"))) {
                return matches(configKeys(), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("set") && config.raw().contains(args[2])) {
                Object existing = config.raw().get(args[2]);
                if (existing instanceof Boolean) {
                    return matches(List.of("true", "false"), args[3]);
                }
                return matches(List.of(String.valueOf(existing)), args[3]);
            }
        }

        if (args[0].equalsIgnoreCase("team") && args.length == 2) {
            return matches(TEAM_SUBCOMMANDS, args[1]);
        }
        if (args[0].equalsIgnoreCase("team") && args.length == 3 && args[1].equalsIgnoreCase("request")) {
            List<String> names = new ArrayList<>();
            for (Player online : sender.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return matches(names, args[2]);
        }
        if (args[0].equalsIgnoreCase("dummy") && args.length == 2) {
            return matches(DUMMY_SUBCOMMANDS, args[1]);
        }
        if (args[0].equalsIgnoreCase("settings") && args.length == 2) {
            return matches(SETTINGS_SUBCOMMANDS, args[1]);
        }
        if (args[0].equalsIgnoreCase("customitem") && args.length == 2) {
            return matches(CUSTOMITEM_SUBCOMMANDS, args[1]);
        }
        if (args[0].equalsIgnoreCase("customitem") && args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            List<String> names = new ArrayList<>();
            for (Material material : config.customDisabledItems()) {
                names.add(material.name());
            }
            return matches(names, args[2]);
        }
        if (args[0].equalsIgnoreCase("debug")) {
            if (args.length == 2) {
                return matches(DEBUG_SUBCOMMANDS, args[1]);
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player online : sender.getServer().getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return matches(names, args[2]);
            }
        }

        return List.of();
    }

    private List<String> configKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : config.raw().getKeys(true)) {
            if (!config.raw().isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private List<String> matches(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix, options, result);
        return result;
    }

    private void handleStatus(CommandSender sender) {
        if (sender instanceof Player player) {
            boolean tagged = combat.isTagged(player);
            sender.sendMessage((tagged ? ChatColor.RED : ChatColor.GREEN)
                    + (tagged ? "You are currently tagged." : "You are not tagged."));
        } else {
            sender.sendMessage(ChatColor.GRAY + "" + combat.taggedPlayers().size() + " player(s) currently tagged.");
        }
    }

    private void handleConfig(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/scdi config <get|set|list> [key] [value]");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                for (String key : config.raw().getKeys(true)) {
                    if (config.raw().isConfigurationSection(key)) {
                        continue;
                    }
                    sender.sendMessage(ChatColor.AQUA + key + ChatColor.GRAY + " = " + ChatColor.YELLOW + config.raw().get(key));
                }
            }
            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scdi config get <key>");
                    return;
                }
                if (!config.raw().contains(args[1])) {
                    sender.sendMessage(ChatColor.RED + "No such key: " + args[1]);
                    return;
                }
                sender.sendMessage(ChatColor.AQUA + args[1] + ChatColor.GRAY + " = " + ChatColor.YELLOW + config.raw().get(args[1]));
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scdi config set <key> <value>");
                    return;
                }
                if (!config.raw().contains(args[1])) {
                    sender.sendMessage(ChatColor.RED + "No such key: " + args[1] + " (use 'config list' to see valid keys).");
                    return;
                }
                Object parsed = parseValue(config.raw().get(args[1]), args[2]);
                config.set(args[1], parsed);
                sender.sendMessage(ChatColor.GREEN + args[1] + " set to " + parsed + ".");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown config subcommand.");
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("scdi.admin")) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
        return false;
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use teams.");
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/scdi team <request|confirm|reset> [player]");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "request" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scdi team request <player>");
                    return;
                }
                Player target = player.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return;
                }
                String error = teams.request(player, target);
                if (error != null) {
                    sender.sendMessage(ChatColor.RED + error);
                    return;
                }
                player.sendMessage(ChatColor.GREEN + "Team request sent to " + target.getName() + ".");
                TextComponent accept = new TextComponent(
                        new ComponentBuilder("[Accept]").color(ChatColor.GREEN).bold(true).create());
                accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/scdi team confirm"));
                accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(new ComponentBuilder("Click to accept").color(ChatColor.GRAY).create())));
                target.sendMessage(ChatColor.GREEN + player.getName() + " wants to team up. ");
                target.spigot().sendMessage(accept);
            }
            case "confirm" -> {
                String error = teams.confirm(player);
                if (error != null) {
                    sender.sendMessage(ChatColor.RED + error);
                } else {
                    sender.sendMessage(ChatColor.GREEN + "You're now teamed up.");
                }
            }
            case "reset" -> {
                teams.reset(player);
                sender.sendMessage(ChatColor.GREEN + "You've left your team.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown team subcommand.");
        }
    }

    private void handleDummy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can manage dummies.");
            return;
        }
        boolean isAdmin = player.hasPermission("scdi.admin");
        if (!isAdmin && !config.dummyAllowPlayerSpawn()) {
            sender.sendMessage(ChatColor.RED + "Dummies are admin-only on this server.");
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/scdi dummy <spawn|remove|removeall>");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                String error = dummies.spawn(player);
                if (error != null) {
                    sender.sendMessage(ChatColor.RED + error);
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Dummy spawned.");
                }
            }
            case "remove" -> {
                Mannequin nearest = nearestDummy(player);
                if (nearest == null) {
                    sender.sendMessage(ChatColor.RED + "No dummy nearby.");
                    return;
                }
                dummies.remove(nearest);
                sender.sendMessage(ChatColor.GREEN + "Dummy removed.");
            }
            case "removeall" -> {
                if (!isAdmin) {
                    sender.sendMessage(ChatColor.RED + "Only admins can remove all dummies.");
                    return;
                }
                int count = dummies.removeAll();
                sender.sendMessage(ChatColor.GREEN + "Removed " + count + " dummies.");
            }
            case "invincible" -> {
                Mannequin nearest = nearestDummy(player);
                if (nearest == null) {
                    sender.sendMessage(ChatColor.RED + "No dummy nearby.");
                    return;
                }
                dummies.setInvincible(nearest, true);
                sender.sendMessage(ChatColor.GREEN + "Dummy made invincible.");
            }
            case "mortal" -> {
                Mannequin nearest = nearestDummy(player);
                if (nearest == null) {
                    sender.sendMessage(ChatColor.RED + "No dummy nearby.");
                    return;
                }
                dummies.setInvincible(nearest, false);
                sender.sendMessage(ChatColor.GREEN + "Dummy made mortal.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown dummy subcommand.");
        }
    }

    private Mannequin nearestDummy(Player player) {
        Mannequin nearest = null;
        double nearestDistSq = 16 * 16;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof Mannequin mannequin && dummies.isDummy(mannequin)) {
                double distSq = mannequin.getLocation().distanceSquared(player.getLocation());
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = mannequin;
                }
            }
        }
        return nearest;
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have personal settings.");
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "Your warning preferences (toggle with e.g. /scdi settings armor-warning):");
            sender.sendMessage(ChatColor.AQUA + "armor-warning" + ChatColor.GRAY + " = " + warnings.armorWarningPref(player));
            sender.sendMessage(ChatColor.AQUA + "armor-warning-sound" + ChatColor.GRAY + " = " + warnings.armorWarningSoundPref(player));
            sender.sendMessage(ChatColor.AQUA + "inventory-warning" + ChatColor.GRAY + " = " + warnings.inventoryWarningPref(player));
            sender.sendMessage(ChatColor.AQUA + "inventory-warning-sound" + ChatColor.GRAY + " = " + warnings.inventoryWarningSoundPref(player));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "armor-warning" -> {
                boolean value = !warnings.armorWarningPref(player);
                warnings.setArmorWarningPref(player, value);
                sender.sendMessage(ChatColor.GREEN + "armor-warning set to " + value);
            }
            case "armor-warning-sound" -> {
                boolean value = !warnings.armorWarningSoundPref(player);
                warnings.setArmorWarningSoundPref(player, value);
                sender.sendMessage(ChatColor.GREEN + "armor-warning-sound set to " + value);
            }
            case "inventory-warning" -> {
                boolean value = !warnings.inventoryWarningPref(player);
                warnings.setInventoryWarningPref(player, value);
                sender.sendMessage(ChatColor.GREEN + "inventory-warning set to " + value);
            }
            case "inventory-warning-sound" -> {
                boolean value = !warnings.inventoryWarningSoundPref(player);
                warnings.setInventoryWarningSoundPref(player, value);
                sender.sendMessage(ChatColor.GREEN + "inventory-warning-sound set to " + value);
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown setting.");
        }
    }

    private void handleCustomItem(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/scdi customitem <add|remove|list> [material]");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                var items = config.customDisabledItems();
                if (items.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No custom disabled items configured.");
                    return;
                }
                for (Material material : items) {
                    sender.sendMessage(ChatColor.AQUA + material.name());
                }
            }
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scdi customitem add <material>");
                    return;
                }
                Material material = Material.matchMaterial(args[1]);
                if (material == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown material: " + args[1]);
                    return;
                }
                config.addCustomDisabledItem(material);
                sender.sendMessage(ChatColor.GREEN + material.name() + " added to custom disabled items.");
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /scdi customitem remove <material>");
                    return;
                }
                Material material = Material.matchMaterial(args[1]);
                if (material == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown material: " + args[1]);
                    return;
                }
                config.removeCustomDisabledItem(material);
                sender.sendMessage(ChatColor.GREEN + material.name() + " removed from custom disabled items.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown customitem subcommand.");
        }
    }

    /** /scdi debug tag|untag [player] - force a player into or out of combat
     * instantly, without needing a real hit to land. For testing everything
     * downstream of a tag (disguise, warnings, armor flash, actionbar,
     * below-name timer) without a second player or real PvP damage. */
    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/scdi debug <tag|untag> [player]");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = sender.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage from console: /scdi debug <tag|untag> <player>");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "tag" -> {
                combat.tag(target);
                sender.sendMessage(ChatColor.GREEN + "Tagged " + target.getName() + ".");
            }
            case "untag" -> {
                combat.release(target);
                sender.sendMessage(ChatColor.GREEN + "Released " + target.getName() + ".");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown debug subcommand.");
        }
    }

    /** Coerces the typed-in string to match the existing value's type, so
     * "/scdi config set combat.pve-mode true" doesn't silently store the
     * literal string "true" where a boolean was expected. */
    private Object parseValue(Object existing, String input) {
        if (existing instanceof Boolean) {
            return Boolean.parseBoolean(input);
        }
        if (existing instanceof Integer) {
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                return existing;
            }
        }
        if (existing instanceof Long) {
            try {
                return Long.parseLong(input);
            } catch (NumberFormatException e) {
                return existing;
            }
        }
        if (existing instanceof Double) {
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                return existing;
            }
        }
        return input;
    }
}
