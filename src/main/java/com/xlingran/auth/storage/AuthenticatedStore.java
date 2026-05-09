package com.xlingran.auth.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 已认证玩家集合：内存中的 {@link Set} 与插件数据目录下 {@code authenticated.yml} 双写。
 * <p>
 * {@link #load()} 会清空内存后从文件重建；新认证通过 {@link #addAuthenticated(Player)} 写入并立即 {@link #save()}。
 */
public final class AuthenticatedStore {

    private final JavaPlugin plugin;
    /** 与 Spigot 默认习惯一致：位于插件数据文件夹根目录 */
    private final File dataFile;
    private final Set<UUID> authenticated = new HashSet<>();

    public AuthenticatedStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "authenticated.yml");
    }

    /** 从磁盘重建内存集合；文件不存在时创建空列表文件 */
    public void load() {
        authenticated.clear();
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
            return;
        }
        if (!dataFile.exists()) {
            YamlConfiguration empty = new YamlConfiguration();
            empty.set("players", new ArrayList<String>());
            try {
                empty.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create authenticated.yml", e);
            }
            return; // 新建文件后内存即为空集合，无需再读
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : yaml.getStringList("players")) {
            try {
                authenticated.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid UUID in authenticated.yml: " + s);
            }
        }
    }

    /** 将当前内存中的 UUID 列表完整写回 YAML（覆盖文件） */
    public void save() {
        List<String> list = new ArrayList<>();
        for (UUID id : authenticated) {
            list.add(id.toString());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("players", list);
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save authenticated.yml", e);
        }
    }

    /**
     * 将玩家标记为已认证；若该 UUID 首次加入集合则立即持久化。
     *
     * @return {@code true} 表示本次调用新写入了认证状态并已保存
     */
    public boolean addAuthenticated(Player player) {
        if (authenticated.add(player.getUniqueId())) {
            save();
            return true;
        }
        return false;
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }
}
