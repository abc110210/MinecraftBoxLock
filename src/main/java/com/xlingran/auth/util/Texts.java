package com.xlingran.auth.util;

import org.bukkit.ChatColor;

/**
 * 与聊天相关的字符串工具（无状态）。
 */
public final class Texts {

    private Texts() {
    }

    /**
     * 将 {@code &} 形式颜色/格式代码转为 Minecraft 解析用的 § 序列。
     *
     * @param s 可为 null，此时返回空串
     */
    public static String colorize(String s) {
        if (s == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
