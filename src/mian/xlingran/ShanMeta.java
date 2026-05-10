package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 玩家头颅缓存管理器
 * 用于缓存玩家头颅数据到本地，避免频繁向 Mojang API 请求
 */
public class ShanMeta {
	
	private static Plugin plugin;
	private static File cacheFile;
	// 使用 ConcurrentHashMap 保证线程安全
	private static Map<UUID, CachedHead> headCache = new ConcurrentHashMap<>();
	// 缓存完整的头颅物品（包含皮肤纹理）
	private static Map<UUID, ItemStack> cachedHeadItems = new ConcurrentHashMap<>();
	
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
	 * 创建带缓存的玩家头颅（优先使用本地缓存，不联网）
	 * @param player 离线玩家对象
	 * @param displayName 显示名称（包含颜色代码）
	 * @return 玩家头颅物品
	 */
	public static ItemStack createCachedPlayerHead(OfflinePlayer player, String displayName) {
		if (player == null) {
			return createEmptyHead(displayName);
		}
		
		UUID playerUUID = player.getUniqueId();
		
		// 优先检查缓存的头颅物品
		if (cachedHeadItems.containsKey(playerUUID)) {
			return cloneHeadWithDisplayName(cachedHeadItems.get(playerUUID), displayName);
		}
		
		// 检查缓存是否有效（通过 UUID 和名称）
		CachedHead cachedHead = headCache.get(playerUUID);
		if (cachedHead != null && !cachedHead.isExpired()) {
			// 尝试从 Bukkit 本地缓存获取玩家对象（不会联网，因为服务器已缓存过）
			OfflinePlayer cachedPlayer = Bukkit.getOfflinePlayer(cachedHead.getUuid());
			if (cachedPlayer != null) {
				ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) head.getItemMeta();
				if (meta != null) {
					meta.setDisplayName(displayName);
					meta.setOwningPlayer(cachedPlayer);
					head.setItemMeta(meta);
					// 缓存这个头颅
					cachedHeadItems.put(playerUUID, head.clone());
					return head;
				}
			}
		}
		
		// 缓存无效且 Bukkit 本地没有缓存，返回默认头颅
		return createEmptyHead(displayName);
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
				if (!value.isExpired() && cachedHeadItems.containsKey(entry.getKey())) {
					// 使用缓存的头颅
					return cloneHeadWithDisplayName(cachedHeadItems.get(entry.getKey()), displayName);
				}
			}
		}
		
		// 缓存中没有，通过名称获取玩家
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
		return createCachedPlayerHead(offlinePlayer, displayName);
	}
	
	/**
	 * 从缓存数据创建头颅物品（已废弃，保留兼容）
	 */
	@Deprecated
	private static ItemStack createHeadFromCache(CachedHead cachedHead, String displayName) {
		return createEmptyHead(displayName);
	}
	
	/**
	 * 创建空头颅（默认 Steve 皮肤）
	 */
	private static ItemStack createEmptyHead(String displayName) {
		ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(displayName);
			head.setItemMeta(meta);
		}
		return head;
	}
	
	/**
	 * 复制缓存的头颅并修改显示名称
	 */
	private static ItemStack cloneHeadWithDisplayName(ItemStack cachedHead, String displayName) {
		ItemStack clone = cachedHead.clone();
		SkullMeta meta = (SkullMeta) clone.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(displayName);
			clone.setItemMeta(meta);
		}
		return clone;
	}
	
	/**
	 * 更新缓存（线程安全）
	 */
	private static synchronized void updateCache(UUID uuid, String playerName) {
		headCache.put(uuid, new CachedHead(uuid, playerName, System.currentTimeMillis()));
		scheduleSave();
	}
	
	/**
	 * 防抖保存缓存（避免频繁IO）
	 */
	private static synchronized void scheduleSave() {
		if (saveScheduled) {
			return; // 已经有保存任务在排队
		}
		
		saveScheduled = true;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			// 创建快照，避免保存时数据被修改
			Map<UUID, CachedHead> snapshot = new HashMap<>(headCache);
			saveCache(snapshot);
			saveScheduled = false;
		}, SAVE_DELAY_TICKS);
	}
	
	/**
	 * 预缓存玩家头颅（玩家加入时调用）
	 * @param player 在线玩家对象
	 */
	public static void precachePlayerHead(Player player) {
		if (player == null || plugin == null) {
			return;
		}
		
		UUID playerUUID = player.getUniqueId();
		String playerName = player.getName();
		
		// 检查缓存是否已存在且有效
		if (cachedHeadItems.containsKey(playerUUID)) {
			// 缓存有效，更新时间戳
			CachedHead cachedHead = headCache.get(playerUUID);
			if (cachedHead != null) {
				cachedHead.setTimestamp(System.currentTimeMillis());
				scheduleSave();
			}
			return;
		}
		
		// 使用延迟任务异步预缓存，给服务器一些缓冲时间
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
			try {
				// 创建头颅并缓存
				ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) head.getItemMeta();
				
				if (meta != null) {
					// 设置玩家所有者，这会触发一次 Mojang API 请求（异步执行，不影响主线程）
					meta.setOwningPlayer(player);
					head.setItemMeta(meta);
					
					// 缓存完整的头颅物品（包含皮肤纹理）
					cachedHeadItems.put(playerUUID, head.clone());
					headCache.put(playerUUID, new CachedHead(playerUUID, playerName, System.currentTimeMillis()));
					scheduleSave();
					
					plugin.getLogger().info("已预缓存玩家头颅: " + playerName + " (" + playerUUID + ")");
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "预缓存玩家头颅失败: " + playerName, e);
			}
		}, 20L); // 延迟1秒执行，避免玩家刚加入就请求
	}
	
	/**
	 * 清理过期缓存（线程安全）
	 */
	public static synchronized void cleanExpiredCache() {
		int before = headCache.size();
		headCache.values().removeIf(CachedHead::isExpired);
		int removedCount = before - headCache.size();
		
		// 同步清理头颅物品缓存
		for (Map.Entry<UUID, CachedHead> entry : headCache.entrySet()) {
			if (!cachedHeadItems.containsKey(entry.getKey())) {
				cachedHeadItems.remove(entry.getKey());
			}
		}
		// 清理不存在于 headCache 中的头颅物品
		cachedHeadItems.keySet().removeIf(uuid -> !headCache.containsKey(uuid));
		
		if (removedCount > 0) {
			saveCache(new HashMap<>(headCache));
			plugin.getLogger().info("已清理 " + removedCount + " 个过期的头颅缓存");
		}
	}
	
	/**
	 * 保存缓存到文件
	 * @param dataToSave 要保存的数据快照
	 */
	private static void saveCache(Map<UUID, CachedHead> dataToSave) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
			oos.writeObject(dataToSave);
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
		} catch (IOException | ClassCastException e) {
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
