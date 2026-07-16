package dev.sacdj.scdi.util;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * GUIs can't take free-text input directly, so numeric/string config edits
 * fall back to "type your value in chat next" - this tracks who's mid-prompt
 * and routes their next chat line to a callback instead of the public chat,
 * the same pattern most inventory-menu plugins use for this exact problem.
 */
public final class ChatInputManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void awaitInput(Player player, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), onInput);
    }

    public boolean isAwaiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> callback = pending.remove(event.getPlayer().getUniqueId());
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        // run back on the main thread - this event fires async, and the
        // callback will be touching inventories/config, neither of which is
        // safe to do off the main thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(message));
    }
}
