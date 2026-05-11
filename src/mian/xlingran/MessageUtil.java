package mian.xlingran;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息工具类
 * 用于管理插件所有消息并处理Windows控制台乱码问题
 */
public class MessageUtil {
	
	private static Plugin plugin;
	private static YamlConfiguration messageConfig;
	
	// 消息缓存
	private static final Map<String, String> messageCache = new HashMap<>();
	
	/**
	 * 初始化消息系统
	 */
	public static void init(Plugin p) {
		plugin = p;
		loadMessages();
	}
	
	/**
	 * 加载消息配置
	 */
	public static void loadMessages() {
		File messageFile = new File(plugin.getDataFolder(), "Message.yml");
		if (messageFile.exists()) {
			messageConfig = YamlConfiguration.loadConfiguration(messageFile);
			messageCache.clear();
			// 缓存所有消息
			for (String key : messageConfig.getKeys(false)) {
				messageCache.put(key, messageConfig.getString(key, ""));
			}
		}
	}
	
	/**
	 * 重载消息配置
	 */
	public static void reloadMessages() {
		loadMessages();
	}
	
	/**
	 * 获取消息（带变量替换）
	 * @param key 消息键
	 * @param variables 变量映射，如 Map.of("player", playerName)
	 * @return 处理后的消息
	 */
	public static String getMessage(String key, Map<String, String> variables) {
		String message = messageCache.getOrDefault(key, "&c消息未找到: " + key);
		if (variables != null) {
			for (Map.Entry<String, String> entry : variables.entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue());
			}
		}
		return message.replace('&', '§');
	}
	
	/**
	 * 获取消息（无变量）
	 */
	public static String getMessage(String key) {
		return getMessage(key, null);
	}
	
	/**
	 * 发送消息给玩家
	 */
	public static void sendMessage(Player player, String key) {
		player.sendMessage(getMessage(key));
	}
	
	/**
	 * 发送消息给玩家（带变量）
	 */
	public static void sendMessage(Player player, String key, Map<String, String> variables) {
		player.sendMessage(getMessage(key, variables));
	}
	
	/**
	 * 发送控制台消息（兼容Windows）
	 * @param message 消息内容
	 */
	public static void sendConsole(String message) {
		try {
			System.out.println(toPlatformCompatible(message));
		} catch (Exception e) {
			System.out.println(message);
		}
	}
	
	/**
	 * 转换为平台兼容的消息
	 * @param message 原始消息
	 * @return 平台兼容的消息
	 */
	private static String toPlatformCompatible(String message) {
		if (message == null) {
			return "";
		}
		
		// Windows CMD 使用 GBK 编码，PowerShell 使用 UTF-8
		// Minecraft 服务器日志通常使用 UTF-8
		// 这里保持原始消息，让 Bukkit 自动处理编码
		return message;
	}
	
	/**
	 * 发送带颜色代码的消息到控制台
	 * @param message 消息内容（包含§颜色代码）
	 */
	public static void sendConsoleColored(String message) {
		// 将§符号转换为控制台的ANSI颜色代码
		String converted = convertColorCodes(message);
		sendConsole(converted);
	}
	
	/**
	 * 转换颜色代码为ANSI
	 * @param message 包含§颜色代码的消息
	 * @return ANSI格式的消息
	 */
	private static String convertColorCodes(String message) {
		return message.replace("§a", "\u001B[32m")  // 绿色
		              .replace("§b", "\u001B[96m")  // 浅蓝色
		              .replace("§c", "\u001B[31m")  // 红色
		              .replace("§d", "\u001B[35m")  // 紫色
		              .replace("§e", "\u001B[33m")  // 黄色
		              .replace("§f", "\u001B[37m")  // 白色
		              .replace("§6", "\u001B[33m")  // 金色
		              .replace("§8", "\u001B[90m")  // 灰色
		              .replace("§9", "\u001B[34m")  // 蓝色
		              .replace("§r", "\u001B[0m")   // 重置
		              .replace("§l", "\u001B[1m");  // 粗体
	}
}
