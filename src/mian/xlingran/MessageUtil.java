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
		String message = messageCache.getOrDefault(key, getHardcodedMessage(key));
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
	 * 硬编码后备消息（与 Message.yml 同步）
	 */
	private static String getHardcodedMessage(String key) {
		switch (key) {
			// 放置箱子
			case "PlacePriBox": return "&a你放置了一个私有的箱子";
			case "PlacePubBox": return "&a你放置了一个公开未上锁的箱子";
			// 独立权限
			case "Individualadd": return "&a成功对该玩家 {player} 添加此箱子权限";
			case "IndividualRemove": return "&c成功对该玩家 {player} 取消此箱子权限";
			// 全局权限
			case "Globaladd": return "&a成功对该玩家 {player} 添加所有箱子权限";
			case "GlobalRemove": return "&a成功对该玩家 {player} 取消所有箱子权限";
			// 锁定开关
			case "LockEnable": return "&a已将箱子设置为 公开 状态，全服玩家可打开但无法破坏";
			case "LockDisable": return "&a已将箱子设置为 &c私有 &a状态";
			// 漏斗开关
			case "FunnelEnable": return "&a已 开启 该箱子的漏斗传输";
			case "FunnelDisable": return "&a已 &c关闭 &a该箱子的漏斗传输";
			// 密码箱
			case "PasswordBoxSet": return "&a请输入 &b4~8位 &a密码(仅支持英文和数字，输入 quit 取消设置)";
			case "PasswordBoxSetSaved": return "&a密码箱设置成功！";
			case "PasswordBoxSetFailed": return "&a已取消设置密码";
			case "PasswordBoxClear": return "&a已清除该箱子的密码保护";
			case "PasswordBoxOpen": return "&c该箱子已启用密码保护，请在聊天栏输入密码(输入 quit 取消打开)";
			case "PasswordBoxCancelOpen": return "&a已取消打开箱子";
			case "PasswordBoxCorrect": return "&a密码正确，请再次右键点击箱子打开";
			case "PasswordBoxErro": return "&a密码错误，请重新输入(输入 quit 取消打开)";
			case "PasswordBoxChange": return "&c密码已被修改，请重新输入密码(输入 quit 取消打开)";
			case "PasswordBoxLengthErro": return "&a密码长度支持 &b4~8位 &a，且仅支持 &b数字+英文 &a组合";
			// 默认状态箱子放置
			case "DefaultPlaceEnable": return "&a已将新放置箱子的默认状态设置为 &c私有";
			case "DefaultPlaceDisable": return "&a已将新放置箱子的默认状态设置为 公开";
			// 默认状态漏斗传输
			case "DefaultFunnelEnable": return "&a已将新放置容器的默认漏斗传输设置为 打开";
			case "DefaultFunnelDisable": return "&a已将心放置容器的默认漏斗传输设置为 &c关闭";
			// 破坏箱子和打开箱子
			case "PrivateBreak": return "&c这个箱子是公开的，您无法破坏！";
			case "LockBox": return "&c这个箱子已被锁定，您无法打开！";
			case "LockBreak": return "&c这个箱子已被锁定，您无法破坏！";
			// 指令提示
			case "CommandOnlyPlayer": return "&c该指令只能由玩家执行！";
			case "CommandNoPermission": return "&c你没有权限执行该命令";
			case "CommandReloadSuccess": return "&a插件已成功重载";
			case "CommandUsageXlr": return "&a用法 /xlr <玩家ID>";
			case "CommandUsageXlrDesc": return "&a将你所有容器全局授权给指定玩家(包括新放置容器)";
			case "CommandUsageXlrdel": return "&a用法 /xlrdel <玩家名称>";
			case "CommandUsageXlrdelDesc": return "&a取消对玩家的全局授权";
			case "CommandPlayerNotFound": return "&c未找到玩家 {player}";
			case "CommandSelfTarget": return "&c不能对自己操作";
			case "CommandAlreadyAuthorized": return "&a玩家 {player} 已获得了全局授权！";
			case "CommandGlobalAddSuccess": return "&a成功将你所有容器，全局授权于玩家 {player}";
			case "CommandNotGlobalAuthorized": return "&c玩家 {player} 没有被全局授权";
			case "CommandGlobalRemoveSuccess": return "&c已取消玩家 {player} 全局授权";
			default: return "&c消息未找到: " + key;
		}
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
