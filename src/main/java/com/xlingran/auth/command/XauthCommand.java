package com.xlingran.auth.command;

import com.xlingran.auth.config.AuthSettings;
import com.xlingran.auth.config.Messages;
import com.xlingran.auth.gui.AuthGuiService;
import com.xlingran.auth.storage.AuthenticatedStore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 处理 {@code /xauth}（及 plugin.yml 中配置的别名）及其子命令。
 * <p>
 * 分支顺序：{@code reload}（管理员）→ {@code help} → {@code status} → 未知参数 → 无参数打开界面。
 */
public final class XauthCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    /** 重载后需与磁盘上的 {@code authenticated.yml}、内存配置保持一致 */
    private final AuthSettings settings;
    private final AuthenticatedStore store;
    private final AuthGuiService guiService;
    private final Messages messages;

    public XauthCommand(JavaPlugin plugin, AuthSettings settings, AuthenticatedStore store, AuthGuiService guiService, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.store = store;
        this.guiService = guiService;
        this.messages = messages;
    }

    /**
     * @return {@code true} 表示已消费该命令（含权限不足等提示），不应再交给其它执行器；{@code false} 仅在不识别命令名时返回
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("xauth")) {
            return false;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xlingranauth.admin")) {
                sender.sendMessage(messages.line("messages.no-permission", "&c没有权限。"));
                return true;
            }
            // 顺序：先 reloadConfig，再解析到 AuthSettings，最后从磁盘刷新 UUID 集合
            plugin.reloadConfig();
            settings.reload(plugin.getConfig());
            store.load();
            sender.sendMessage(messages.line("messages.reloaded", "&a配置已重新加载。"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            messages.sendHelp(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.line("messages.only-players", "&c仅玩家。"));
                return true;
            }
            Player p = (Player) sender;
            if (store.isAuthenticated(p)) {
                p.sendMessage(messages.line("messages.already-authenticated", "&7已认证。"));
            } else {
                p.sendMessage(ChatColor.GRAY + "您尚未完成创作者身份认证，输入 " + ChatColor.YELLOW + "/xauth" + ChatColor.GRAY + " 打开界面。");
            }
            return true;
        }

        if (args.length > 0) {
            sender.sendMessage(ChatColor.GRAY + "未知子命令。使用 " + ChatColor.YELLOW + "/xauth help" + ChatColor.GRAY + " 查看帮助。");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.line("messages.only-players", "&c仅玩家可使用该命令。"));
            return true;
        }

        if (!sender.hasPermission("xlingranauth.use")) {
            sender.sendMessage(messages.line("messages.no-permission", "&c没有权限。"));
            return true;
        }

        Player player = (Player) sender;
        if (store.isAuthenticated(player)) {
            player.sendMessage(messages.line("messages.already-authenticated", "&7您已经认证过了。"));
            return true;
        }
        guiService.open(player);
        return true;
    }
}
