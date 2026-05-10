package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 玩家头颅缓存管理器
 * 用于缓存玩家头颅数据到本地，避免频繁向 Mojang API 请求
 */
public class ShanMeta {
	
	private static Plugin plugin;
	private static File cacheFile;
	private static Map<UUID, CachedHead> headCache = new HashMap<>();
	
	// 防抖保存相关
	private static volatile boolean saveScheduled = false;
	private static final long SAVE_DELAY_TICKS = 100L; // 5秒后保存
	
	/**
	 * 初始化缓存系统
	 */
	public static void init(Plugin p) {
		plugin = p;
		cacheFile = new File(plugin.getDataFolder(), "head_cache.dat");
		loadCache();
	}
	
	/**
	 * 创建带缓存的玩家头颅
	 * @param player 离线玩家对象
	 * @param displayName 显示名称（包含颜色代码）
	 * @return 玩家头颅物品
	 */
	public static ItemStack createCachedPlayerHead(OfflinePlayer player, String displayName) {
		UUID playerUUID = player.getUniqueId();
		
		// 检查缓存是否有效
		CachedHead cachedHead = headCache.get(playerUUID);
		if (cachedHead != null && !cachedHead.isExpired()) {
			// 使用缓存数据创建头颅
			return createHeadFromCache(cachedHead, displayName);
		}
		
		// 缓存无效或不存在，从 Bukkit API 获取
		ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		
		if (meta != null) {
			meta.setDisplayName(displayName);
			meta.setOwningPlayer(player);
			head.setItemMeta(meta);
			
			// 更新缓存
			updateCache(playerUUID, player.getName());
		}
		
		return head;
	}
	
	/**
	 * 通过玩家名称创建头颅（会先查找 UUID）
	 * @param playerName 玩家名称
	 * @param displayName 显示名称（包含颜色代码）
	 * @return 玩家头颅物品
	 */
	public static ItemStack createCachedPlayerHeadByName(String playerName, String displayName) {
		// 先从缓存中查找是否有该玩家的记录
		for (Map.Entry<UUID, CachedHead> entry : headCache.entrySet()) {
			CachedHead value = entry.getValue();
			if (value != null && value.getPlayerName() != null && value.getPlayerName().equals(playerName)) {
				if (!value.isExpired()) {
					// 使用缓存的 UUID 获取玩家对象
					OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
					return createCachedPlayerHead(offlinePlayer, displayName);
				}
			}
		}
		
		// 缓存中没有，通过名称获取玩家
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
		return createCachedPlayerHead(offlinePlayer, displayName);
	}
	
	/**
	 * 从缓存数据创建头颅物品
	 */
	private static ItemStack createHeadFromCache(CachedHead cachedHead, String displayName) {
		ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		
		if (meta != null) {
			meta.setDisplayName(displayName);
			
			// 使用缓存的 UUID 获取玩家对象
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(cachedHead.getUuid());
			meta.setOwningPlayer(offlinePlayer);
			
			head.setItemMeta(meta);
		}
		
		return head;
	}
	
	/**
	 * 更新缓存
	 */
	private static void updateCache(UUID uuid, String playerName) {
		headCache.put(uuid, new CachedHead(uuid, playerName, System.currentTimeMillis()));
		scheduleSave();
	}
	
	/**
	 * 防抖保存缓存（避免频繁IO）
	 */
	private static void scheduleSave() {
		if (saveScheduled) {
			return; // 已经有保存任务在排队
		}
		
		saveScheduled = true;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			saveCache();
			saveScheduled = false;
		}, SAVE_DELAY_TICKS);
	}
	
	/**
	 * 预缓存玩家头颅（玩家加入时调用）
	 * @param player 在线玩家对象
	 */
	public static void precachePlayerHead(Player player) {
		if (player == null) {
			return;
		}
		
		UUID playerUUID = player.getUniqueId();
		String playerName = player.getName();
		
		// 检查缓存是否已存在且有效
		CachedHead cachedHead = headCache.get(playerUUID);
		if (cachedHead != null && !cachedHead.isExpired()) {
			// 缓存有效，只需要更新时间戳
			cachedHead.setTimestamp(System.currentTimeMillis());
			// 延迟保存，避免频繁IO
			scheduleSave();
			return;
		}
		
		// 使用延迟任务预缓存，给服务器一些缓冲时间
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
			try {
				// 创建临时头颅来触发 Bukkit 的玩家数据加载
				// 这会从 Mojang 获取玩家纹理数据并缓存到服务器
				ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) head.getItemMeta();
				
				if (meta != null) {
					// 设置玩家所有者，这会触发一次 Mojang API 请求
					meta.setOwningPlayer(player);
					head.setItemMeta(meta);
					
					// 更新缓存
					headCache.put(playerUUID, new CachedHead(playerUUID, playerName, System.currentTimeMillis()));
					saveCache();
					
					plugin.getLogger().info("已预缓存玩家头颅: " + playerName + " (" + playerUUID + ")");
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "预缓存玩家头颅失败: " + playerName, e);
			}
		}, 20L); // 延迟1秒执行，避免玩家刚加入就请求
	}
	
	/**
	 * 清理过期缓存
	 */
	public static void cleanExpiredCache() {
		int before = headCache.size();
		headCache.values().removeIf(CachedHead::isExpired);
		int removedCount = before - headCache.size();
		
		if (removedCount > 0) {
			saveCache();
			plugin.getLogger().info("已清理 " + removedCount + " 个过期的头颅缓存");
		}
	}
	
	/**
	 * 保存缓存到文件
	 */
	private static void saveCache() {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
			oos.writeObject(headCache);
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "无法保存头颅缓存", e);
		}
	}
	
	/**
	 * 从文件加载缓存
	 */
	@SuppressWarnings("unchecked")
	private static void loadCache() {
		if (!cacheFile.exists()) {
			return;
		}
		
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
			Object obj = ois.readObject();
			if (obj instanceof Map) {
				headCache = (Map<UUID, CachedHead>) obj;
				plugin.getLogger().info("已加载 " + headCache.size() + " 个头颅缓存");
				
				// 加载后清理过期缓存
				cleanExpiredCache();
			}
		} catch (ClassNotFoundException e) {
			// 类结构变更（从内部类移到外部类），旧缓存不兼容，自动重建
			plugin.getLogger().info("检测到旧版本缓存格式，正在重建缓存...");
			headCache.clear();
			if (cacheFile.exists()) {
				cacheFile.delete();
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "无法加载头颅缓存，将重建缓存", e);
			// 如果加载失败，清空缓存
			headCache.clear();
			if (cacheFile.exists()) {
				cacheFile.delete();
			}
		}
	}
	
	/**
	 * 手动清除所有缓存
	 */
	public static void clearCache() {
		headCache.clear();
		if (cacheFile.exists()) {
			cacheFile.delete();
		}
		plugin.getLogger().info("已清除所有头颅缓存");
	}
	
	/**
	 * 获取缓存统计信息
	 */
	public static String getCacheStats() {
		int validCount = 0;
		int expiredCount = 0;
		
		for (CachedHead cachedHead : headCache.values()) {
			if (cachedHead.isExpired()) {
				expiredCount++;
			} else {
				validCount++;
			}
		}
		
		return String.format("头颅缓存统计 - 有效: %d, 过期: %d, 总计: %d", 
			validCount, expiredCount, headCache.size());
	}
}
