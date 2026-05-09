package com.xlingran.auth.config;

import com.xlingran.auth.util.Texts;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 认证 GUI 与进服行为的运行时配置，从 {@code config.yml} 映射而来。
 * <p>
 * {@link #getGuiTitleResolved()} 必须与打开界面时使用的标题一致，监听器据此识别“是否为本插件的箱子”。
 */
public final class AuthSettings {

    private static final int DEFAULT_ROWS = 3;
    /** 原版箱子式菜单每行固定 9 格 */
    private static final int COLS = 9;

    /** 已解析 {@code &} 颜色代码后的界面标题 */
    private String guiTitleResolved = "";
    private int slotInfo = 13;
    private int slotConfirm = 11;
    private int slotCancel = 15;
    private int guiRows = DEFAULT_ROWS;
    /** {@code gui.open-on-join}：进服后是否自动弹出认证界面 */
    private boolean openOnJoin;

    /**
     * 从给定配置快照填充字段（通常为 {@link org.bukkit.plugin.java.JavaPlugin#getConfig()}）。
     */
    public void reload(FileConfiguration cfg) {
        String rawTitle = cfg.getString("gui.title", "&a&lXlingran Auth");
        guiTitleResolved = Texts.colorize(rawTitle);
        slotInfo = cfg.getInt("slots.info", 13);
        slotConfirm = cfg.getInt("slots.confirm", 11);
        slotCancel = cfg.getInt("slots.cancel", 15);
        guiRows = cfg.getInt("gui.rows", DEFAULT_ROWS);
        if (guiRows < 1) {
            guiRows = DEFAULT_ROWS; // 防止非法行数导致 size 为 0
        }
        openOnJoin = cfg.getBoolean("gui.open-on-join", false);
    }

    /** @return 与 {@link org.bukkit.inventory.Inventory#getTitle()} 比对用，已着色 */
    public String getGuiTitleResolved() {
        return guiTitleResolved;
    }

    /** @return 说明物品槽位（config: {@code slots.info}） */
    public int getSlotInfo() {
        return slotInfo;
    }

    /** @return 确认认证槽位（config: {@code slots.confirm}） */
    public int getSlotConfirm() {
        return slotConfirm;
    }

    /** @return 关闭界面槽位（config: {@code slots.cancel}） */
    public int getSlotCancel() {
        return slotCancel;
    }

    /** @return 界面总格数，须为 9 的倍数（{@code gui.rows * 9}） */
    public int getGuiSize() {
        return guiRows * COLS;
    }

    public boolean isOpenOnJoin() {
        return openOnJoin;
    }
}
