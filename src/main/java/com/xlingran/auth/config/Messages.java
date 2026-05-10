package com.xlingran.auth.config;

import com.xlingran.auth.util.Texts;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 玩家可见字符串的集中读取点：路径对应 {@code config.yml}，{@code &} 会转为颜色。
 * <p>
 * 依赖 {@link JavaPlugin#getConfig()}，因此须在 {@link JavaPlugin#reloadConfig()} 之后才会反映磁盘变更。
 */
public final class Messages {

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param path 配置路径，如 {@code messages.no-permission}
     * @param def  键缺失或值为 null 时的兜底原文（可含 {@code &}）
     */
    public String line(String path, String def) {
        return Texts.colorize(plugin.getConfig().getString(path, def));
    }

    /** 输出多行帮助，内容来自 {@code messages.help-*} */
    public void sendHelp(org.bukkit.command.CommandSender sender) {
        FileConfiguration c = plugin.getConfig();
        sender.sendMessage(Texts.colorize(c.getString("messages.help-header", "&6=== XlingranAuth ===")));
        sender.sendMessage(Texts.colorize(c.getString("messages.help-line-1", "&e/xauth")));
        sender.sendMessage(Texts.colorize(c.getString("messages.help-line-2", "&e/xauth reload")));
        sender.sendMessage(Texts.colorize(c.getString("messages.help-line-3", "&e/xauth status")));
    }
}
