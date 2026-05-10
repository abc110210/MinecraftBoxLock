package com.xlingran.auth.listener;

import com.xlingran.auth.config.AuthSettings;
import com.xlingran.auth.gui.AuthGuiService;
import com.xlingran.auth.storage.AuthenticatedStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 玩家加入服务器后的可选行为：在配置开启时延迟打开认证界面。
 * <p>
 * 延迟 1 秒（20 tick）再打开，避免与进服标题包、其它插件“首次进服 GUI”抢同一刻。
 */
public final class AuthJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final AuthSettings settings;
    private final AuthenticatedStore store;
    private final AuthGuiService guiService;

    public AuthJoinListener(JavaPlugin plugin, AuthSettings settings, AuthenticatedStore store, AuthGuiService guiService) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.guiService = guiService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!settings.isOpenOnJoin()) {
            return;
        }
        Player p = event.getPlayer();
        if (!p.hasPermission("xlingranauth.use") || store.isAuthenticated(p)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !store.isAuthenticated(p)) {
                guiService.open(p);
            }
        }, 20L); // 20L = 1 秒
    }
}
