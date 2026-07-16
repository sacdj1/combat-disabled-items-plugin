package dev.sacdj.scdi.command;

import dev.sacdj.scdi.combat.CombatManager;
import dev.sacdj.scdi.config.ConfigCodec;
import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.menu.MainMenu;
import dev.sacdj.scdi.menu.MenuManager;
import dev.sacdj.scdi.util.ChatInputManager;
import net.md_5.bungee.api.ChatColor;
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

    private static final List<String> SUBCOMMANDS = List.of("menu", "reload", "status", "config", "export", "import");
    private static final List<String> CONFIG_SUBCOMMANDS = List.of("get", "set", "list");

    private final ScdiConfig config;
    private final CombatManager combat;
    private final MenuManager menuManager;
    private final ChatInputManager chatInput;
    private final ConfigCodec codec;

    public ScdiCommand(ScdiConfig config, CombatManager combat, MenuManager menuManager,
                        ChatInputManager chatInput, ConfigCodec codec) {
        this.config = config;
        this.combat = combat;
        this.menuManager = menuManager;
        this.chatInput = chatInput;
        this.codec = codec;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                menuManager.open(player, new MainMenu(config, chatInput));
            } else {
                sender.sendMessage(ChatColor.GRAY + "/scdi menu | reload | status | config <get|set|list> | export | import <code>");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu" -> {
                if (sender instanceof Player player) {
                    menuManager.open(player, new MainMenu(config, chatInput));
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can open the menu.");
                }
            }
            case "reload" -> {
                config.reload();
                sender.sendMessage(ChatColor.GREEN + "Combat Disabled Items config reloaded.");
            }
            case "status" -> handleStatus(sender);
            case "config" -> handleConfig(sender, Arrays.copyOfRange(args, 1, args.length));
            case "export" -> {
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
