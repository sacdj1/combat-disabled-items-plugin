package dev.sacdj.scdi.menu;

import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.util.ChatInputManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class MainMenu implements MenuScreen {

    private final ScdiConfig config;
    private final ChatInputManager chatInput;

    public MainMenu(ScdiConfig config, ChatInputManager chatInput) {
        this.config = config;
        this.chatInput = chatInput;
    }

    @Override
    public Inventory build(Player player) {
        ScdiMenuHolder holder = new ScdiMenuHolder(this);
        Inventory inv = Bukkit.createInventory(holder, 27, "Combat Disabled Items");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, MenuItems.filler());
        }
        inv.setItem(9, MenuItems.nav(Material.IRON_SWORD, "Combat", "Duration, tagging rules, PvE mode, teams."));
        inv.setItem(10, MenuItems.nav(Material.FIREWORK_ROCKET, "Disabled Items", "Which items get locked."));
        inv.setItem(11, MenuItems.nav(Material.BARRIER, "Disguise", "What the locked item looks like."));
        inv.setItem(12, MenuItems.nav(Material.SPYGLASS, "Proximity", "Tag by nearby range, no hit required."));
        inv.setItem(13, MenuItems.nav(Material.DIAMOND_SWORD, "One-Shot", "Instant-kill detection/announcement."));
        inv.setItem(14, MenuItems.nav(Material.PAPER, "Warnings", "Default disabled-item chat warnings."));
        inv.setItem(15, MenuItems.nav(Material.NOTE_BLOCK, "Sounds", "Combat/safe sound + pitch."));
        inv.setItem(16, MenuItems.nav(Material.OAK_SIGN, "Display", "Actionbar and title toggles."));
        inv.setItem(17, MenuItems.nav(Material.ARMOR_STAND, "Dummy", "Test-dummy spawn limits and behavior."));
        inv.setItem(21, MenuItems.nav(Material.WHITE_BANNER, "Teams",
                "Click for the team commands - any player can use these."));
        return inv;
    }

    @Override
    public void onClick(Player player, int slot, MenuManager manager) {
        MenuScreen next = switch (slot) {
            case 9 -> new CombatMenu(config, chatInput);
            case 10 -> new DisabledItemsMenu(config, chatInput);
            case 11 -> new DisguiseMenu(config, chatInput);
            case 12 -> new ProximityMenu(config, chatInput);
            case 13 -> new OneShotMenu(config, chatInput);
            case 14 -> new WarningsMenu(config, chatInput);
            case 15 -> new SoundsMenu(config, chatInput);
            case 16 -> new DisplayMenu(config, chatInput);
            case 17 -> new DummyMenu(config, chatInput);
            case 21 -> {
                showTeamCommands(player);
                yield null;
            }
            default -> null;
        };
        if (next != null) {
            manager.open(player, next);
        }
    }

    /** Teams are a per-player action, not a server config setting, so this
     * doesn't get its own toggle screen the way everything else here does -
     * just surfaces the /scdi team command (which any player, not just
     * admins, can already use) as clickable buttons. */
    private void showTeamCommands(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "Team commands:");
        sendSuggest(player, "/scdi team request <player>", "Request a player join your team.");
        sendRun(player, "/scdi team confirm", "Accept the most recent pending request sent to you.");
        sendRun(player, "/scdi team reset", "Leave your current team.");
    }

    private void sendSuggest(Player player, String command, String hover) {
        TextComponent component = new TextComponent(new ComponentBuilder(command).color(ChatColor.AQUA).create());
        component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(new ComponentBuilder(hover).color(ChatColor.GRAY).create())));
        player.spigot().sendMessage(component);
    }

    private void sendRun(Player player, String command, String hover) {
        TextComponent component = new TextComponent(new ComponentBuilder(command).color(ChatColor.AQUA).create());
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(new ComponentBuilder(hover).color(ChatColor.GRAY).create())));
        player.spigot().sendMessage(component);
    }
}
