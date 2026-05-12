package mian.xlingran;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/* 消息管理 */
public class MessageUtil {
	
	private static Plugin plugin;
	private static YamlConfiguration messageConfig;
	
	// 消息缓存
	private static final Map<String, String> messageCache = new HashMap<>();
	
	public static void init(Plugin p) {
		plugin = p;
		loadMessages();
	}
	
	public static void loadMessages() {
		File messageFile = new File(plugin.getDataFolder(), "Message.yml");
		if (messageFile.exists()) {
			messageConfig = YamlConfiguration.loadConfiguration(messageFile);
			messageCache.clear();
			for (String key : messageConfig.getKeys(false)) {
				messageCache.put(key, messageConfig.getString(key, ""));
			}
		}
	}
	
	public static void reloadMessages() {
		loadMessages();
	}

	public static String getMessage(String key, Map<String, String> variables) {
		String message = messageCache.getOrDefault(key, getHardcodedMessage(key));
		if (variables != null) {
			for (Map.Entry<String, String> entry : variables.entrySet()) {
				message = message.replace("{" + entry.getKey() + "}", entry.getValue());
			}
		}
		return message.replace('&', '§');
	}
	
	public static String getMessage(String key) {
		return getMessage(key, null);
	}
	
	private static String getHardcodedMessage(String key) {
		switch (key) {
			// 放置箱子
			case "PlacePriBox": return "§a[§e寄寄之家§a] §6你放置了一个 §c私有 §6的容器";
			case "PlacePubBox": return "§a[§e寄寄之家§a] §6你放置了一个 §a公开 §6的容器";
			// 独立权限
			case "Individualadd": return "§a[§e寄寄之家§a] §6成功对玩家 §b{player} §6添加此容器权限";
			case "IndividualRemove": return "§a[§e寄寄之家§a] §c移除玩家 §b{player} §c的此容器权限";
			// 全局权限
			case "Globaladd": return "§a[§e寄寄之家§a] §6成功对该玩家 §b{player} §6添加全局授权";
			case "GlobalRemove": return "§a[§e寄寄之家§a] §c移除玩家 §b{player} §c全局授权";
			// 锁定开关
			case "LockEnable": return "§a[§e寄寄之家§a] §6已将容器切换为 §a公开 §6状态";
			case "LockDisable": return "§a[§e寄寄之家§a] §6已将容器切换为 §c私有 §6状态";
			// 漏斗开关
			case "FunnelEnable": return "§a[§e寄寄之家§a] §6漏斗传输开关设置为 §a开启 §6状态";
			case "FunnelDisable": return "§a[§e寄寄之家§a] §6漏斗传输开关设置为 §c关闭 §6状态";
			// 密码箱
			case "PasswordBoxSet": return "§a[§e寄寄之家§a] §6请输入 §d4~8 §6位 §9数字+英文 §6组合的密码(输入 §cquit §6可退出设置)";
			case "PasswordBoxSetSaved": return "§a[§e寄寄之家§a] §6密码设置成功!";
			case "PasswordBoxSetFailed": return "§a[§e寄寄之家§a] §c已取消密码设置!";
			case "PasswordBoxClear": return "§a[§e寄寄之家§a] §c已清除该容器的密码保护";
			case "PasswordBoxOpen": return "§a[§e寄寄之家§a] §6该箱子已启用密码保护,请输入密码(输入 §cquit §6可退出密码输入)";
			case "PasswordBoxCancelOpen": return "§a[§e寄寄之家§a] §c已取消打开该密码箱";
			case "PasswordBoxCorrect": return "§a[§e寄寄之家§a] §6密码输入正确，点击打开该密码箱";
			case "PasswordBoxErro": return "§a[§e寄寄之家§a] §c密码输入错误请重新输入!§6（输入 §cquit §6取消打开）";
			case "PasswordBoxChange": return "§a[§e寄寄之家§a] §c密码已被修改，请输入新密码§6(输入 §cquit §6取消输入)";
			case "PasswordBoxLengthErro": return "§a[§e寄寄之家§a] §6密码长度支持 §d4~8 §6位，且仅支持 §9数字+英文 §c组合";
			// 默认状态箱子放置
			case "DefaultPlaceEnable": return "§a[§e寄寄之家§a] §6已将新放置的容器默认设置为 §c私有 §6状态";
			case "DefaultPlaceDisable": return "§a[§e寄寄之家§a] §6已将新放置的容器默认设置为 §a公开 §6状态";
			// 默认状态漏斗传输
			case "DefaultFunnelEnable": return "§a[§e寄寄之家§a] §6已将新放置的容器漏斗传输设置为 §a打开";
			case "DefaultFunnelDisable": return "§a[§e寄寄之家§a] §6已将新放置的容器漏斗传输设置为 §c关闭";
			// 破坏箱子和打开箱子
			case "PrivateBreak": return "§a[§e寄寄之家§a] §6这个容器是 §a公开 §6状态，你无法破坏,只能打开";
			case "LockBox": return "§a[§e寄寄之家§a] §c这个容器已被上锁，当前无法打开!";
			case "LockBreak": return "§a[§e寄寄之家§a] §c这个容器已被上锁，当前无法破坏!";
			// 指令提示
			case "CommandOnlyPlayer": return "§a[§e寄寄之家§a] §c该指令只能由玩家执行!";
			case "CommandNoPermission": return "§a[§e寄寄之家§a] §c你没有权限执行该命令";
			case "CommandReloadSuccess": return "§a[§e寄寄之家§a] §6插件已成功重载";
			case "CommandUsageXlr": return "§a[§e寄寄之家§a] §6用法 /xlr §b<玩家ID>";
			case "CommandUsageXlrDesc": return "§a[§e寄寄之家§a] §6将你所有容器全局授权给指定玩家(包括新放置容器)";
			case "CommandUsageXlrdel": return "§a[§e寄寄之家§a] §6用法 /xlrdel §b<玩家名称>";
			case "CommandUsageXlrdelDesc": return "§a[§e寄寄之家§a] §6取消对玩家的全局授权";
			case "CommandPlayerNotFound": return "§a[§e寄寄之家§a] §6未找到玩家 §b{player}";
			case "CommandSelfTarget": return "§a[§e寄寄之家§a] §c不能对自己操作";
			case "CommandAlreadyAuthorized": return "§a[§e寄寄之家§a] §6玩家 §b{player} §6已获得了全局授权！";
			case "CommandGlobalAddSuccess": return "§a[§e寄寄之家§a] §6成功将你所有容器，全局授权于玩家 §b{player}";
			case "CommandNotGlobalAuthorized": return "§a[§e寄寄之家§a] §c玩家 §b{player} §c没有被全局授权";
			case "CommandGlobalRemoveSuccess": return "§a[§e寄寄之家§a] §c已取消玩家 §b{player} §c全局授权";
			default: return "§c消息未找到: " + key;
		}
	}
	
	public static void sendMessage(Player player, String key) {
		player.sendMessage(getMessage(key));
	}
	
	public static void sendMessage(Player player, String key, Map<String, String> variables) {
		player.sendMessage(getMessage(key, variables));
	}
	
	public static void sendConsole(String message) {
		try {
			System.out.println(toPlatformCompatible(message));
		} catch (Exception e) {
			System.out.println(message);
		}
	}
	
	private static String toPlatformCompatible(String message) {
		if (message == null) {
			return "";
		}
		
		return message;
	}
	
	public static void sendConsoleColored(String message) {
		String converted = convertColorCodes(message);
		sendConsole(converted);
	}
	
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
