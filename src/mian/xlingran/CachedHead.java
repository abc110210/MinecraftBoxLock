package mian.xlingran;

import java.io.Serializable;
import java.util.UUID;

/**
 * 缓存的头颅数据结构
 * 用于序列化存储玩家头颅缓存信息（包含纹理数据，避免重新请求 Mojang API）
 */
public class CachedHead implements Serializable {
	private static final long serialVersionUID = 2L;
	
	/** 缓存过期时间（毫秒），默认7天 */
	private static final long CACHE_EXPIRY_TIME = 7 * 24 * 60 * 60 * 1000;
	
	/** 玩家UUID */
	private UUID uuid;
	
	/** 玩家名称 */
	private String playerName;
	
	/** 缓存时间戳 */
	private long timestamp;
	
	/** Mojang 纹理 value（base64） */
	private String textureValue;
	
	/** Mojang 纹理 signature */
	private String textureSignature;
	
	/**
	 * 构造函数（无纹理数据）
	 */
	public CachedHead(UUID uuid, String playerName, long timestamp) {
		this.uuid = uuid;
		this.playerName = playerName;
		this.timestamp = timestamp;
		this.textureValue = null;
		this.textureSignature = null;
	}
	
	/**
	 * 构造函数（包含纹理数据）
	 */
	public CachedHead(UUID uuid, String playerName, long timestamp, String textureValue, String textureSignature) {
		this.uuid = uuid;
		this.playerName = playerName;
		this.timestamp = timestamp;
		this.textureValue = textureValue;
		this.textureSignature = textureSignature;
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}
	
	public String getPlayerName() {
		return playerName;
	}
	
	public void setPlayerName(String playerName) {
		this.playerName = playerName;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getTextureValue() {
		return textureValue;
	}
	
	public void setTextureValue(String textureValue) {
		this.textureValue = textureValue;
	}
	
	public String getTextureSignature() {
		return textureSignature;
	}
	
	public void setTextureSignature(String textureSignature) {
		this.textureSignature = textureSignature;
	}
	
	/**
	 * 是否有完整的纹理数据
	 */
	public boolean hasTexture() {
		return textureValue != null && !textureValue.isEmpty();
	}
	
	/**
	 * 检查缓存是否已过期
	 * @return 是否过期
	 */
	public boolean isExpired() {
		return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
	}
}
