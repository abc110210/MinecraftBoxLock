package com.xlingran.auth.gui;

import com.xlingran.auth.config.AuthSettings;
import com.xlingran.auth.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证箱界面：与多行箱子相同格子数的自定义标题容器（{@code Bukkit.createInventory}）。
 * <p>
 * 使用 1.12 染色玻璃板 data：5=绿、15=黑；功能物品槽位由 {@link AuthSettings} 提供。
 */
public final class AuthGuiService {

    private final JavaPlugin plugin;
    private final AuthSettings settings;

    public AuthGuiService(JavaPlugin plugin, AuthSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /**
     * 用当前配置铺底、描边并放置说明 / 确认 / 关闭物品，然后为玩家打开界面。
     */
    public void open(Player player) {
        int size = settings.getGuiSize();
        Inventory gui = Bukkit.createInventory(null, size, settings.getGuiTitleResolved());

        // STAINED_GLASS_PANE data: 5 = 绿色（背景），15 = 黑色（边框）
        ItemStack greenGlass = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 5);
        for (int i = 0; i < size; i++) {
            gui.setItem(i, greenGlass);
        }

        ItemStack blackGlass = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15);
        applyBorder(gui, size, blackGlass);

        FileConfiguration cfg = plugin.getConfig();

        gui.setItem(settings.getSlotInfo(), namedBook(cfg));
        gui.setItem(settings.getSlotConfirm(), namedStack(Material.EMERALD, cfg, "confirm-item.name", "&a确认", "confirm-item.lore"));
        gui.setItem(settings.getSlotCancel(), namedStack(Material.BARRIER, cfg, "cancel-item.name", "&c关闭", "cancel-item.lore"));

        player.openInventory(gui);
    }

    /**
     * 在四边覆盖黑色玻璃：顶行、底行、中间各行最左与最右一格。
     */
    private static void applyBorder(Inventory gui, int size, ItemStack blackGlass) {
        if (size < 9) {
            return;
        }
        int rows = size / 9;
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, blackGlass);
        }
        if (rows >= 2) {
            for (int r = 1; r < rows - 1; r++) {
                gui.setItem(r * 9, blackGlass);
                gui.setItem(r * 9 + 8, blackGlass);
            }
        }
        if (rows >= 2) {
            int bottom = (rows - 1) * 9;
            for (int i = 0; i < 9; i++) {
                gui.setItem(bottom + i, blackGlass);
            }
        }
    }

    private ItemStack namedBook(FileConfiguration cfg) {
        ItemStack info = new ItemStack(Material.BOOK, 1);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(Texts.colorize(cfg.getString("creator.item-name", "&e说明")));
            infoMeta.setLore(colorLore(cfg.getStringList("creator.lore")));
            info.setItemMeta(infoMeta);
        }
        return info;
    }

    private static ItemStack namedStack(Material material, FileConfiguration cfg, String nameKey, String nameDef, String loreKey) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.colorize(cfg.getString(nameKey, nameDef)));
            meta.setLore(colorLore(cfg.getStringList(loreKey)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static List<String> colorLore(List<String> lore) {
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            out.add(Texts.colorize(line));
        }
        return out;
    }
}
