package mian.xlingran;

import java.io.Serializable;
import java.util.UUID;

/**
 * 缓存的头颅数据结构
 * 用于序列化存储玩家头颅缓存信息
 */
public class CachedHead implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/** 缓存过期时间（毫秒），默认24小时 */
	private static final long CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000;
	
	/** 玩家UUID */
	private UUID uuid;
	
	/** 玩家名称 */
	private String playerName;
	
	/** 缓存时间戳 */
	private long timestamp;
	
	/**
	 * 构造函数
	 * @param uuid 玩家UUID
	 * @param playerName 玩家名称
	 * @param timestamp 缓存时间戳
	 */
	public CachedHead(UUID uuid, String playerName, long timestamp) {
		this.uuid = uuid;
		this.playerName = playerName;
		this.timestamp = timestamp;
	}
	
	/**
	 * 获取玩家UUID
	 * @return UUID
	 */
	public UUID getUuid() {
		return uuid;
	}
	
	/**
	 * 设置玩家UUID
	 * @param uuid UUID
	 */
	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}
	
	/**
	 * 获取玩家名称
	 * @return 玩家名称
	 */
	public String getPlayerName() {
		return playerName;
	}
	
	/**
	 * 设置玩家名称
	 * @param playerName 玩家名称
	 */
	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}
	
	/**
	 * 获取缓存时间戳
	 * @return 时间戳
	 */
	public long getTimestamp() {
		return timestamp;
	}
	
	/**
	 * 设置缓存时间戳
	 * @param timestamp 时间戳
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	/**
	 * 检查缓存是否已过期
	 * @return 是否过期
	 */
	public boolean isExpired() {
		return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
	}
}
