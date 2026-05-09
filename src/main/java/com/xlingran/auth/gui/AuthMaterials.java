package com.xlingran.auth.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * GUI 染色玻璃板：1.13+ 使用扁平化材质名；1.12.x 使用 {@code STAINED_GLASS_PANE} + 染料值。
 * <p>
 * 不嵌入 XSeries：旧版 XSeries 在静态初始化时会解析 Bukkit 版本字符串，无法识别 Paper 26.x 的 {@code 26.1.2} 格式，
 * 会导致 {@code ExceptionInInitializerError} 并在首次打开界面时崩溃。
 */
public final class AuthMaterials {

    private AuthMaterials() {
    }

    /** 1.12 {@link Material#STAINED_GLASS_PANE} 染料值：黄绿 */
    private static final short LEGACY_LIME = 5;
    /** 1.12 染料值：黑 */
    private static final short LEGACY_BLACK = 15;

    public static ItemStack limeStainedGlassPane(int amount) {
        return stainedGlassPane(amount, "LIME_STAINED_GLASS_PANE", LEGACY_LIME);
    }

    public static ItemStack blackStainedGlassPane(int amount) {
        return stainedGlassPane(amount, "BLACK_STAINED_GLASS_PANE", LEGACY_BLACK);
    }

    private static ItemStack stainedGlassPane(int amount, String flatName, short legacyData) {
        int amt = Math.max(1, amount);
        Material modern = Material.matchMaterial(flatName);
        if (modern != null) {
            return new ItemStack(modern, amt);
        }
        Material legacyBase = Material.matchMaterial("STAINED_GLASS_PANE");
        if (legacyBase != null) {
            ItemStack stack = new ItemStack(legacyBase, amt);
            stack.setDurability(legacyData);
            return stack;
        }
        Material pane = Material.matchMaterial("GLASS_PANE");
        if (pane != null) {
            return new ItemStack(pane, amt);
        }
        return new ItemStack(Material.BARRIER, amt);
    }
}
