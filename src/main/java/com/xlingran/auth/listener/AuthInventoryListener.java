package com.xlingran.auth.listener;

import com.xlingran.auth.config.AuthSettings;
import com.xlingran.auth.config.Messages;
import com.xlingran.auth.storage.AuthenticatedStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 认证箱内点击：通过标题精确匹配本插件界面，防止误伤其它插件菜单。
 * <p>
 * 使用 {@link InventoryClickEvent#getRawSlot()} 判断点击的是容器格还是玩家背包（背包格为 {@code >= size}）。
 */
public final class AuthInventoryListener implements Listener {

    private final AuthSettings settings;
    private final AuthenticatedStore store;
    private final Messages messages;

    public AuthInventoryListener(AuthSettings settings, AuthenticatedStore store, Messages messages) {
        this.settings = settings;
        this.store = store;
        this.messages = messages;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null || event.getWhoClicked() == null) {
            return;
        }
        // 标题必须与打开界面时一致（含颜色）；改 config 后须 /xauth reload
        String title = event.getInventory().getTitle();
        if (title == null || !title.equals(settings.getGuiTitleResolved())) {
            return;
        }

        event.setCancelled(true); // 禁止拿走展示用玻璃与按钮

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        int size = settings.getGuiSize();
        // 仅处理上方容器格子；点在玩家背包里 raw 会 >= size，此处忽略
        if (raw < 0 || raw >= size) {
            return;
        }

        if (raw == settings.getSlotConfirm()) {
            store.addAuthenticated(player);
            player.sendMessage(messages.line("messages.authenticated", "&a认证成功。"));
            player.closeInventory();
            return;
        }
        if (raw == settings.getSlotCancel()) {
            player.sendMessage(messages.line("messages.closed", "&7已关闭。"));
            player.closeInventory();
        }
    }
}
