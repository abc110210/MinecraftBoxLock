package com.xlingran.auth.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 认证箱 {@link InventoryHolder}，用于在 Paper 新版 API 下识别本插件界面（不依赖 {@code Inventory#getTitle()}）。
 */
public final class AuthGuiHolder implements InventoryHolder {

    private Inventory inventory;

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
