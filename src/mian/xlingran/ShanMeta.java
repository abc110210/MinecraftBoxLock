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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 玩家头颅缓存管理器
 * 持久化缓存头颅纹理数据，完全避免实时请求 Mojang API
 */
public class ShanMeta {
	
	private static Plugin plugin;
	private static File cacheFile;
	// 持久化缓存：存储玩家头颅纹理数据
	private static Map<UUID, CachedHead> headCache = new ConcurrentHashMap<>();
	
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
	 * 创建带缓存的玩家头颅（优先使用本地纹理数据，完全不联网）
	 * @param player 离线玩家对象
	 * @param displayName 显示名称（包含颜色代码）
	 * @return 玩家头颅物品
	 */
	public static ItemStack createCachedPlayerHead(OfflinePlayer player, String displayName) {
		if (player == null) {
			return createEmptyHead(displayName);
		}
		
		UUID playerUUID = player.getUniqueId();
		CachedHead cachedHead = headCache.get(playerUUID);
		
		// 如果有缓存的纹理数据，直接使用纹理创建头颅（不联网）
		if (cachedHead != null && !cachedHead.isExpired() && cachedHead.hasTexture()) {
			return createHeadFromTexture(cachedHead, displayName);
		}
		
		// 没有纹理缓存，返回默认头颅
		return createEmptyHead(displayName);
	}
	
	/**
	 * 通过玩家名称创建头颅（会先查找 UUID）
	 * @param playerName 玩家名称
	 * @param displayName 显示名称（包含颜色代码）
	 * @return 玩家头颅物品
	 */
	public static ItemStack createCachedPlayerHeadByName(String playerName, String displayName) {
		// 先从缓存中查找
		for (Map.Entry<UUID, CachedHead> entry : headCache.entrySet()) {
			CachedHead value = entry.getValue();
			if (value != null && value.getPlayerName() != null && value.getPlayerName().equals(playerName)) {
				if (!value.isExpired() && value.hasTexture()) {
					return createHeadFromTexture(value, displayName);
				}
			}
		}
		return createEmptyHead(displayName);
	}
	
	/**
	 * 从纹理数据创建头颅（完全不联网）
	 */
	private static ItemStack createHeadFromTexture(CachedHead cachedHead, String displayName) {
		try {
			ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			
			if (meta != null) {
				meta.setDisplayName(displayName);
				
				// 使用 Spigot 1.21.1 API 直接设置纹理（不触发 Mojang API 请求）
				org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(
					cachedHead.getUuid(), 
					cachedHead.getPlayerName() != null ? cachedHead.getPlayerName() : "Unknown"
				);
				org.bukkit.profile.PlayerTextures textures = profile.getTextures();
				java.net.URL textureUrl = new java.net.URL(
					"data:application/json;base64," + cachedHead.getTextureValue()
				);
				textures.setSkin(textureUrl);
				profile.setTextures(textures);
				meta.setOwnerProfile(profile);
				
				head.setItemMeta(meta);
			}
			
			return head;
		} catch (Exception e) {
			if (plugin != null) {
				plugin.getLogger().log(Level.WARNING, "从纹理创建头颅失败: " + cachedHead.getPlayerName(), e);
			}
			return createEmptyHead(displayName);
		}
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
	 * 防抖保存缓存
	 */
	private static synchronized void scheduleSave() {
		if (saveScheduled) {
			return;
		}
		
		saveScheduled = true;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			Map<UUID, CachedHead> snapshot = new HashMap<>(headCache);
			saveCache(snapshot);
			saveScheduled = false;
		}, SAVE_DELAY_TICKS);
	}
	
	/**
	 * 预缓存玩家头颅（玩家加入时调用，异步获取纹理）
	 */
	public static void precachePlayerHead(Player player) {
		if (player == null || plugin == null) {
			return;
		}
		
		UUID playerUUID = player.getUniqueId();
		String playerName = player.getName();
		
		// 检查缓存是否已存在且有效
		CachedHead cachedHead = headCache.get(playerUUID);
		if (cachedHead != null && !cachedHead.isExpired() && cachedHead.hasTexture()) {
			cachedHead.setTimestamp(System.currentTimeMillis());
			scheduleSave();
			return;
		}
		
		// 异步预缓存
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
			try {
				ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
				SkullMeta meta = (SkullMeta) head.getItemMeta();
				
				if (meta != null) {
					meta.setOwningPlayer(player);
					head.setItemMeta(meta);
					
					// 提取纹理数据
					SkullMeta loadedMeta = (SkullMeta) head.getItemMeta();
					if (loadedMeta != null && loadedMeta.hasOwner()) {
						org.bukkit.profile.PlayerProfile profile = loadedMeta.getOwnerProfile();
						if (profile != null) {
							String textureValue = extractTextureValue(profile);
							String textureSignature = extractTextureSignature(profile);
							
							if (textureValue != null) {
								CachedHead newCache = new CachedHead(
									playerUUID, 
									playerName, 
									System.currentTimeMillis(),
									textureValue,
									textureSignature
								);
								headCache.put(playerUUID, newCache);
								scheduleSave();
								plugin.getLogger().info("已预缓存玩家头颅: " + playerName + " (" + playerUUID + ")");
							}
						}
					}
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "预缓存玩家头颅失败: " + playerName, e);
			}
		}, 20L);
	}
	
	/**
	 * 从 PlayerProfile 提取纹理 value
	 */
	private static String extractTextureValue(org.bukkit.profile.PlayerProfile profile) {
		try {
			org.bukkit.profile.PlayerTextures textures = profile.getTextures();
			java.net.URL skinUrl = textures.getSkin();
			if (skinUrl != null) {
				String uri = skinUrl.toString();
				// URL 格式: data:application/json;base64,xxxxx
				if (uri.contains("base64,")) {
					return uri.substring(uri.indexOf("base64,") + 7);
				}
			}
		} catch (Exception e) {
			// 忽略
		}
		return null;
	}
	
	/**
	 * 从 PlayerProfile 提取纹理 signature
	 */
	private static String extractTextureSignature(org.bukkit.profile.PlayerProfile profile) {
		// Spigot 1.21.1 没有直接的 signature API，返回 null
		return null;
	}
	
	/**
	 * 清理过期缓存
	 */
	public static synchronized void cleanExpiredCache() {
		int before = headCache.size();
		headCache.values().removeIf(CachedHead::isExpired);
		int removedCount = before - headCache.size();
		
		if (removedCount > 0) {
			saveCache(new HashMap<>(headCache));
			plugin.getLogger().info("已清理 " + removedCount + " 个过期的头颅缓存");
		}
	}
	
	/**
	 * 保存缓存到文件
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
				cleanExpiredCache();
			}
		} catch (ClassNotFoundException e) {
			plugin.getLogger().info("检测到旧版本缓存格式，正在重建缓存...");
			headCache.clear();
			if (cacheFile.exists()) {
				cacheFile.delete();
			}
		} catch (IOException | ClassCastException | java.io.InvalidClassException e) {
			plugin.getLogger().log(Level.WARNING, "无法加载头颅缓存，将重建缓存", e);
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
		int withTexture = 0;
		
		for (CachedHead cachedHead : headCache.values()) {
			if (cachedHead.isExpired()) {
				expiredCount++;
			} else {
				validCount++;
				if (cachedHead.hasTexture()) {
					withTexture++;
				}
			}
		}
		
		return String.format("头颅缓存统计 - 有效: %d (有纹理: %d), 过期: %d, 总计: %d", 
			validCount, withTexture, expiredCount, headCache.size());
	}
}
