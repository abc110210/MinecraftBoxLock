package com.xlingran.auth;

import com.xlingran.auth.command.XauthCommand;
import com.xlingran.auth.config.AuthSettings;
import com.xlingran.auth.config.Messages;
import com.xlingran.auth.gui.AuthGuiService;
import com.xlingran.auth.listener.AuthInventoryListener;
import com.xlingran.auth.listener.AuthJoinListener;
import com.xlingran.auth.storage.AuthenticatedStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * XlingranAuth 插件主类。
 * <p>
 * 职责：加载默认配置、初始化各模块、注册命令与事件监听器。
 * 具体业务分散在 {@code config} / {@code storage} / {@code gui} / {@code command} / {@code listener} 包中。
 */
public class XlingranAuth extends JavaPlugin {

    /** 界面标题、槽位、进服是否弹窗等运行时配置（随 reload 更新）。 */
    private AuthSettings settings;
    /** 已认证玩家 UUID，与 {@code authenticated.yml} 同步。 */
    private AuthenticatedStore authenticatedStore;
    /** 认证箱 UI 的构建与打开。 */
    private AuthGuiService guiService;
    /** 从 {@code config.yml} 取文案并转换颜色代码。 */
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        settings = new AuthSettings();
        settings.reload(getConfig());

        authenticatedStore = new AuthenticatedStore(this);
        authenticatedStore.load();

        messages = new Messages(this);
        guiService = new AuthGuiService(this, settings);

        // 命令必须在 plugin.yml 中声明，否则 getCommand 为 null
        PluginCommand cmd = getCommand("xauth");
        if (cmd != null) {
            cmd.setExecutor(new XauthCommand(this, settings, authenticatedStore, guiService, messages));
        } else {
            getLogger().warning("Command 'xauth' missing from plugin.yml; commands will not work.");
        }

        getServer().getPluginManager().registerEvents(new AuthInventoryListener(settings, authenticatedStore, messages), this);
        getServer().getPluginManager().registerEvents(new AuthJoinListener(this, settings, authenticatedStore, guiService), this);

        getLogger().info("XlingranAuth has been enabled!");
        getLogger().info("Author: shan | Website: www.xlingran.com");
    }

    @Override
    public void onDisable() {
        // 确保进服后点选认证的数据在卸载前写入磁盘
        if (authenticatedStore != null) {
            authenticatedStore.save();
        }
        getLogger().info("XlingranAuth has been disabled!");
    }

    /** @return 当前 GUI 与行为相关配置快照，供扩展读取 */
    public AuthSettings getSettings() {
        return settings;
    }

    /** @return 认证数据存储，可供其它插件查询或配合自定义逻辑 */
    public AuthenticatedStore getAuthenticatedStore() {
        return authenticatedStore;
    }

    /** @return 认证界面服务，供外部在合适时机主动弹出界面 */
    public AuthGuiService getGuiService() {
        return guiService;
    }

    /**
     * 查询玩家是否已在本次服务周期内完成认证（数据来自内存 + 持久化文件）。
     * 供外部插件或脚本调用，与早期单类版本 API 保持一致。
     */
    public boolean isAuthenticated(Player player) {
        return authenticatedStore != null && authenticatedStore.isAuthenticated(player);
    }

    /**
     * 直接为玩家打开认证箱界面。
     * 不检查权限、不拦截已认证玩家；若需与玩家输入 {@code /xauth} 行为一致，请自行判断。
     */
    public void openAuthGUI(Player player) {
        if (guiService != null) {
            guiService.open(player);
        }
    }
}
