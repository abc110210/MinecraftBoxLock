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

public class ShanMeta {
	
	private static Plugin plugin;
	private static File cacheFile;
	
	/** 缓存有效期：14 天 */
	private static final long CACHE_EXPIRY_TIME = 14L * 24 * 60 * 60 * 1000;
	
	/** 每 10 分钟缓存一个头颅 */
	private static final long CACHE_TASK_INTERVAL_TICKS = 20L * 60 * 10; // 600 秒
	
	// 持久化缓存：存储玩家头颅纹理数据
	private static Map<UUID, CachedHead> headCache = new ConcurrentHashMap<>();
	
	/** 定时缓存任务 ID */
	private static int cacheTaskId = -1;
	
	/**
	 * 初始化缓存系统
	 */
	public static void init(Plugin p) {
		plugin = p;
		
		// 确保插件数据目录存在
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdirs();
		}
		
		cacheFile = new File(plugin.getDataFolder(), "head_cache.dat");
		plugin.getLogger().info("头颅缓存文件路径: " + cacheFile.getAbsolutePath());
		
		loadCache();
		startPeriodicCache();
	}
	
	public static ItemStack createCachedPlayerHead(OfflinePlayer player, String displayName) {
		if (player == null) {
			return createEmptyHead(displayName);
		}
		
		UUID playerUUID = player.getUniqueId();
		CachedHead cachedHead = headCache.get(playerUUID);
		
		// 有缓存且未过期，直接使用纹理创建头颅
		if (cachedHead != null && !isExpired(cachedHead.getTimestamp()) && cachedHead.hasTexture()) {
			return createHeadFromTexture(cachedHead, displayName);
		}
		
		// 无缓存或已过期，返回默认头颅（等待后台任务缓存）
		return createEmptyHead(displayName);
	}
	
	public static ItemStack createCachedPlayerHeadByName(String playerName, String displayName) {
		for (Map.Entry<UUID, CachedHead> entry : headCache.entrySet()) {
			CachedHead value = entry.getValue();
			if (value != null && value.getPlayerName() != null && value.getPlayerName().equals(playerName)) {
				if (!isExpired(value.getTimestamp()) && value.hasTexture()) {
					return createHeadFromTexture(value, displayName);
				}
			}
		}
		return createEmptyHead(displayName);
	}
	
	private static ItemStack createHeadFromTexture(CachedHead cachedHead, String displayName) {
		try {
			ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			
			if (meta != null) {
				meta.setDisplayName(displayName);
				
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
	
	private static ItemStack createEmptyHead(String displayName) {
		ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(displayName);
			head.setItemMeta(meta);
		}
		return head;
	}
	
	private static void startPeriodicCache() {
		if (cacheTaskId != -1) {
			Bukkit.getScheduler().cancelTask(cacheTaskId);
		}
		
		cacheTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
			try {
				cacheOnePlayerHead();
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "定时缓存任务异常", e);
			}
		}, CACHE_TASK_INTERVAL_TICKS, CACHE_TASK_INTERVAL_TICKS).getTaskId();
	}
	
	private static void cacheOnePlayerHead() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			UUID playerUUID = player.getUniqueId();
			CachedHead cached = headCache.get(playerUUID);
			
			// 如果已缓存且未过期，跳过
			if (cached != null && !isExpired(cached.getTimestamp()) && cached.hasTexture()) {
				continue;
			}
			
			// 找到第一个需要缓存的玩家
			try {
				// 优先使用 Player 对象获取资料（包含已加载的会话皮肤数据）
				org.bukkit.profile.PlayerProfile profile = player.getPlayerProfile();
				String textureValue = extractTextureValue(profile, player.getName());
				
				if (textureValue == null) {
					// plugin.getLogger().info("[头颅缓存] 玩家 " + player.getName() + " 纹理数据尚未加载或请求被拒绝(429)，跳过等待下次");
					continue;
				}
				
				CachedHead newCache = new CachedHead(
					playerUUID,
					player.getName(),
					textureValue,
					System.currentTimeMillis()
				);
				headCache.put(playerUUID, newCache);
				saveCache(headCache);
				// plugin.getLogger().info("[头颅缓存] 成功缓存: " + player.getName() + " (纹理长度: " + textureValue.length() + ")");
			} catch (Exception e) {
				// plugin.getLogger().log(Level.WARNING, "[头颅缓存] 缓存失败: " + player.getName(), e);
			}
			
			// 每次只缓存一个
			return;
		}
	}
	
	private static String extractTextureValue(org.bukkit.profile.PlayerProfile profile, String debugName) {
		try {
			if (profile == null) {
				// plugin.getLogger().info("[头颅缓存] 提取纹理失败: " + debugName + " 资料为空");
				return null;
			}
			org.bukkit.profile.PlayerTextures textures = profile.getTextures();
			java.net.URL skinUrl = textures.getSkin();
			if (skinUrl != null) {
				String urlStr = skinUrl.toString();
				if (urlStr.contains("base64,")) {
					return urlStr.substring(urlStr.indexOf("base64,") + 7);
				}
			}
			// plugin.getLogger().info("[头颅缓存] 提取纹理失败: " + debugName + " 皮肤 URL 未包含 base64 数据");
		} catch (Exception e) {
			// plugin.getLogger().log(Level.WARNING, "[头颅缓存] 提取纹理异常: " + debugName, e);
		}
		return null;
	}
	
	private static boolean isExpired(long timestamp) {
		return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
	}
	
	public static synchronized void clearCache() {
		headCache.clear();
		if (cacheFile.exists()) {
			cacheFile.delete();
		}
		plugin.getLogger().info("已清除所有头颅缓存");
	}
	
	private static synchronized void saveCache(Map<UUID, CachedHead> dataToSave) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
			oos.writeObject(dataToSave);
			plugin.getLogger().info("已保存 " + dataToSave.size() + " 个头颅缓存到: " + cacheFile.getAbsolutePath());
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "无法保存头颅缓存到文件: " + cacheFile.getAbsolutePath(), e);
		}
	}
	
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
				
				// 清理过期缓存
				int before = headCache.size();
				headCache.entrySet().removeIf(e -> isExpired(e.getValue().getTimestamp()));
				int removed = before - headCache.size();
				if (removed > 0) {
					saveCache(headCache);
					plugin.getLogger().info("已清理 " + removed + " 个过期的头颅缓存");
				}
			}
		} catch (ClassNotFoundException e) {
			plugin.getLogger().info("检测到旧版本缓存格式，正在重建缓存...");
			headCache.clear();
			if (cacheFile.exists()) cacheFile.delete();
		} catch (IOException | ClassCastException e) {
			plugin.getLogger().log(Level.WARNING, "无法加载头颅缓存，将重建缓存", e);
			headCache.clear();
			if (cacheFile.exists()) cacheFile.delete();
		}
	}
	
	public static String getCacheStats() {
		int validCount = 0;
		int expiredCount = 0;
		
		for (CachedHead cachedHead : headCache.values()) {
			if (isExpired(cachedHead.getTimestamp())) {
				expiredCount++;
			} else {
				validCount++;
			}
		}
		
		return String.format("头颅缓存统计 - 有效: %d, 过期: %d, 总计: %d", 
			validCount, expiredCount, headCache.size());
	}
}
