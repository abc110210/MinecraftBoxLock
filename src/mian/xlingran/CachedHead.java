package mian.xlingran;

import java.io.*;
import java.util.UUID;

/* 头颅相关 */
public class CachedHead implements Serializable {
	private static final long serialVersionUID = 3L;
	
	private UUID uuid;
	
	private String playerName;
	
	private String textureValue;
	
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
	
	public boolean hasTexture() {
		return textureValue != null && !textureValue.isEmpty();
	}
}
