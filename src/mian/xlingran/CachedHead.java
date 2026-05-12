package mian.xlingran;

import java.io.*;
import java.util.UUID;

/**
 * 缓存的头颅数据结构
 * 包含玩家 UUID、名称、纹理数据和时间戳
 */
public class CachedHead implements Serializable {
	private static final long serialVersionUID = 3L;
	
	/** 玩家UUID */
	private UUID uuid;
	
	/** 玩家名称 */
	private String playerName;
	
	/** Mojang 纹理 value（base64） */
	private String textureValue;
	
	/** 缓存时间戳 */
	private long timestamp;
	
	public CachedHead(UUID uuid, String playerName, String textureValue, long timestamp) {
		this.uuid = uuid;
		this.playerName = playerName;
		this.textureValue = textureValue;
		this.timestamp = timestamp;
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public String getPlayerName() {
		return playerName;
	}
	
	public String getTextureValue() {
		return textureValue;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	/**
	 * 是否有完整的纹理数据
	 */
	public boolean hasTexture() {
		return textureValue != null && !textureValue.isEmpty();
	}
}
