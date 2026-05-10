package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
	
	// 缓存过期时间（毫秒），默认24小时
	private static final long CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000;
	
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
			if (entry.getValue().playerName != null && entry.getValue().playerName.equals(playerName)) {
				if (!entry.getValue().isExpired()) {
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
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(cachedHead.uuid);
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
		saveCache();
	}
	
	/**
	 * 清理过期缓存
	 */
	public static void cleanExpiredCache() {
		int removedCount = 0;
		for (Map.Entry<UUID, CachedHead> entry : headCache.entrySet()) {
			if (entry.getValue().isExpired()) {
				headCache.remove(entry.getKey());
				removedCount++;
			}
		}
		
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
		} catch (IOException | ClassNotFoundException e) {
			plugin.getLogger().log(Level.WARNING, "无法加载头颅缓存，将重建缓存", e);
			// 如果加载失败，清空缓存
			headCache.clear();
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
	
	/**
	 * 缓存的头颅数据结构
	 */
	private static class CachedHead implements Serializable {
		private static final long serialVersionUID = 1L;
		
		UUID uuid;
		String playerName;
		long timestamp;
		
		CachedHead(UUID uuid, String playerName, long timestamp) {
			this.uuid = uuid;
			this.playerName = playerName;
			this.timestamp = timestamp;
		}
		
		boolean isExpired() {
			return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
		}
	}
}
