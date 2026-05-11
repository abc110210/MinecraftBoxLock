package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.io.File;

public class ShanGui {
	
	private static Plugin plugin;
	
	// 玩家点击冷却时间记录 (防止点击过快导致GUI失效)
	private static final Map<UUID, Long> playerClickCooldowns = new HashMap<>();
	private static final long CLICK_COOLDOWN_MS = 500; // 500毫秒冷却时间
	
	public static void setPlugin(Plugin p) {
		plugin = p;
	}
	
	/**
	 * 加载GUI配置文件
	 */
	public static void loadGuiConfig(Plugin plugin) {
		File guiFile = new File(plugin.getDataFolder(), "Gui.yml");
		if (guiFile.exists()) {
			guiConfig = YamlConfiguration.loadConfiguration(guiFile);
		}
	}
	
	/**
	 * 检查玩家是否在冷却中
	 * @return true 表示在冷却中，false 表示可以点击
	 */
	private static boolean isOnCooldown(Player player) {
		UUID playerUUID = player.getUniqueId();
		long currentTime = System.currentTimeMillis();
		
		if (playerClickCooldowns.containsKey(playerUUID)) {
			long lastClickTime = playerClickCooldowns.get(playerUUID);
			if (currentTime - lastClickTime < CLICK_COOLDOWN_MS) {
				return true; // 在冷却中
			}
		}
		
		// 更新点击时间
		playerClickCooldowns.put(playerUUID, currentTime);
		return false; // 不在冷却中
	}
	
	// GUI配置
	private static YamlConfiguration guiConfig;
	
	// GUI名称
	private static final String BOX_MANAGE_TITLE = "§e箱子管理";
	private static final String SINGLE_PERMISSION_TITLE = "§b单独权限设置";
	private static final String PERMISSION_ADD_TITLE = "§a权限设置(单独)";
	private static final String PERMISSION_REMOVE_TITLE = "§c取消权限(单独)";
	private static final String GLOBAL_PERMISSION_TITLE = "§b全局权限设置";
	private static final String GLOBAL_ADD_TITLE = "§a权限设置(全局)";
	private static final String GLOBAL_REMOVE_TITLE = "§c取消权限(全局)";
	private static final String MANAGEMENT_PANEL_TITLE = "§b管理面板";
	// GUI行数
	private static final int GUI_ROWS = 3;      // 箱子管理 3行
	private static final int SINGLE_ROWS = 3;   // 单独权限设置 3行
	private static final int PERMISSION_ADD_ROWS = 6;    // 54格 (最大)
	private static final int PERMISSION_REMOVE_ROWS = 6; // 54格 (带分页)
	private static final int GLOBAL_ROWS = 3;   // 全局权限设置 3行
	private static final int MANAGEMENT_PANEL_ROWS = 1; // 管理面板 1行
	private static final int PLAYERS_PER_PAGE = 28; // 每页显示玩家数量 (4行×7列)
	private static final int PREV_PAGE_SLOT = 47;   // 上一页按钮 (倒数第7格, 黄绿色玻璃)
	private static final int NEXT_PAGE_SLOT = 51;   // 下一页按钮 (倒数第3格, 黄绿色玻璃)
	private static final int RETURN_SLOT = 53;      // 返回按钮 (全局权限GUI使用)
	
	/**
	 * 从配置文件创建GUI物品（不含变量替换）
	 */
	private static ItemStack createGuiItemFromConfig(YamlConfiguration config, String path, Material material) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			// 读取名称
			String name = config.getString(path + ".name");
			if (name != null) {
				meta.setDisplayName(name.replace('&', '§'));
			}
			
			// 读取Lore
			List<String> lore = config.getStringList(path + ".Lore");
			if (!lore.isEmpty()) {
				List<String> coloredLore = new ArrayList<>();
				for (String line : lore) {
					coloredLore.add(line.replace('&', '§'));
				}
				meta.setLore(coloredLore);
			}
			
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			item.setItemMeta(meta);
		}
		return item;
	}
	
	/**
	 * 从配置文件创建GUI物品（含变量替换）
	 * @param variableName 变量名，如 "Lockstatus"
	 * @param variableValue 变量值，如 "§a公开"
	 */
	private static ItemStack createGuiItemFromConfigWithVariable(YamlConfiguration config, String path, Material material, String variableName, String variableValue) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			// 读取名称
			String name = config.getString(path + ".name");
			if (name != null) {
				name = name.replace('&', '§').replace("{" + variableName + "}", variableValue);
				meta.setDisplayName(name);
			}
			
			// 读取Lore
			List<String> lore = config.getStringList(path + ".Lore");
			if (!lore.isEmpty()) {
				List<String> coloredLore = new ArrayList<>();
				for (String line : lore) {
					line = line.replace('&', '§').replace("{" + variableName + "}", variableValue);
					coloredLore.add(line);
				}
				meta.setLore(coloredLore);
			}
			
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			item.setItemMeta(meta);
		}
		return item;
	}
	
	/**
	 * 默认箱子管理GUI（硬编码后备方案）
	 */
	private static void openBoxManageGuiDefault(Player player, Block chestBlock, Map<String, UUID> chestOwners, Set<String> publicChests, Set<String> hopperEnabledChests, Map<String, String> chestPasswords) {
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, "§d容器管理");
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack ownerHead = createPlayerHead(chestBlock, chestOwners);
		ItemMeta ownerMeta = ownerHead.getItemMeta();
		if (ownerMeta != null) {
			ownerMeta.setDisplayName("§b独立权限");
			List<String> ownerLore = new ArrayList<>();
			ownerLore.add("");
			ownerLore.add("§8&l- §6授权的玩家可以打开这个容器");
			ownerLore.add("§8&l- §6对单容器的设置");
			ownerMeta.setLore(ownerLore);
			ownerHead.setItemMeta(ownerMeta);
		}
		gui.setItem(10, ownerHead);
		
		ItemStack wheat = createItem(Material.WHEAT, "§b全局权限设置",
			"",
			"§8&l- §6授权玩家可以打开你所有的容器",
			"§8&l- §6对所有容器设置"
		);
		gui.setItem(12, wheat);
		
		String locationKey = getLocationKey(chestBlock);
		boolean isPublic = publicChests != null && publicChests.contains(locationKey);
		String statusColor = isPublic ? "§a公开" : "§c私有";
		
		ItemStack chest = createItem(Material.CHEST, "§b锁定开关",
			"",
			"§3私有 §8&l- §6拥有权限的玩家才能打开",
			"§3公开 §8&l- §6所有的玩家都可以打开你的容器",
			"§8&l- §6点击切换你的容器状态",
			"§8&l- §6容器公开后只能打开，不能破坏",
			"",
			"§9当前状态: " + statusColor
		);
		gui.setItem(14, chest);
		
		boolean isHopperEnabled = hopperEnabledChests != null && hopperEnabledChests.contains(locationKey);
		String hopperStatusColor = isHopperEnabled ? "§a打开" : "§c关闭";
		
		ItemStack hopper = createItem(Material.HOPPER, "§b漏斗开关",
			"",
			"§8&l- §6打开和关闭漏斗传输",
			"§8&l- §6点击切换开关",
			"",
			"§9当前状态: " + hopperStatusColor
		);
		gui.setItem(16, hopper);
		
		boolean hasPassword = chestPasswords != null && chestPasswords.containsKey(locationKey);
		String passwordStatus = hasPassword ? "§a已设置" : "§c未设置";
		
		ItemStack paper = createItem(Material.PAPER, "§b密码箱",
			"",
			"§8&l- §6为箱子设置密码保护",
			"§8&l- §6设置后有密码的玩家可以打开",
			"§8&l- §6会绕过授权",
			"",
			"§9当前状态: " + passwordStatus
		);
		gui.setItem(20, paper);
		
		ItemStack repeater = createItem(Material.REPEATER, "§b管理面板",
			"",
			"§8&l- §6管理容器默认设置",
			"§8&l- §6新放置的容器将按照设置的模式",
			"§8&l- §6默认都是 §c关闭 §6的"
		);
		gui.setItem(22, repeater);
		
		ItemStack barrier = createItem(Material.BARRIER, "§b待开发",
			"§8目前未开放"
		);
		gui.setItem(24, barrier);
		
		player.openInventory(gui);
	}
	
	// 打开箱子管理GUI界面 (3行)
	public static void openBoxManageGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Set<String> publicChests, Set<String> hopperEnabledChests, Map<String, String> chestPasswords, Map<UUID, Boolean> playerDefaultPublicSettings) {
		// 如果配置文件未加载，使用默认硬编码
		if (guiConfig == null) {
			openBoxManageGuiDefault(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords);
			return;
		}
		
		// 从配置读取GUI设置
		String guiTitle = guiConfig.getString("Rongqi.name", "&e箱子管理");
		guiTitle = guiTitle.replace('&', '§');
		// 行数固定为 3 行，不可修改
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, guiTitle);
		
		// 填充黑色玻璃
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < GUI_ROWS * 9; i++) {
			gui.setItem(i, blackGlass);
		}
		
		String locationKey = getLocationKey(chestBlock);
		boolean isPublic = publicChests != null && publicChests.contains(locationKey);
		boolean isHopperEnabled = hopperEnabledChests != null && hopperEnabledChests.contains(locationKey);
		boolean hasPassword = chestPasswords != null && chestPasswords.containsKey(locationKey);
		
		// 单独权限按钮
		if (guiConfig.contains("Rongqi.Alone")) {
			String materialStr = guiConfig.getString("Rongqi.Alone.material", "PLAYER_HEAD");
			int slot = guiConfig.getInt("Rongqi.Alone.slot", 10);
			
			if (materialStr.equals("PLAYER_HEAD")) {
				ItemStack ownerHead = createPlayerHead(chestBlock, chestOwners);
				// 从配置读取名称和Lore
				String name = guiConfig.getString("Rongqi.Alone.name", "&e单独权限").replace('&', '§');
				ItemMeta meta = ownerHead.getItemMeta();
				if (meta != null) {
					meta.setDisplayName(name);
					List<String> lore = guiConfig.getStringList("Rongqi.Alone.Lore");
					if (!lore.isEmpty()) {
						List<String> coloredLore = new ArrayList<>();
						for (String line : lore) {
							coloredLore.add(line.replace('&', '§'));
						}
						meta.setLore(coloredLore);
					}
					ownerHead.setItemMeta(meta);
				}
				gui.setItem(slot, ownerHead);
			} else {
				Material material = Material.matchMaterial(materialStr);
				if (material != null) {
					ItemStack item = createGuiItemFromConfig(guiConfig, "Rongqi.Alone", material);
					gui.setItem(slot, item);
				}
			}
		}
		
		// 全局权限按钮
		if (guiConfig.contains("Rongqi.Global")) {
			String materialStr = guiConfig.getString("Rongqi.Global.material", "WHEAT");
			int slot = guiConfig.getInt("Rongqi.Global.slot", 12);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "Rongqi.Global", material);
				gui.setItem(slot, item);
			}
		}
		
		// 锁定开关按钮
		if (guiConfig.contains("Rongqi.Lock")) {
			String materialStr = guiConfig.getString("Rongqi.Lock.material", "CHEST");
			int slot = guiConfig.getInt("Rongqi.Lock.slot", 14);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				// 从 Variable 配置读取变量值
				String lockStatus = isPublic ? 
					guiConfig.getString("Variable.LockstatusOn", "&a公开") : 
					guiConfig.getString("Variable.LockstatusOFF", "&c私有");
				lockStatus = lockStatus.replace('&', '§');
				ItemStack item = createGuiItemFromConfigWithVariable(guiConfig, "Rongqi.Lock", material, "Lockstatus", lockStatus);
				gui.setItem(slot, item);
			}
		}
		
		// 漏斗开关按钮
		if (guiConfig.contains("Rongqi.Funnel")) {
			String materialStr = guiConfig.getString("Rongqi.Funnel.material", "HOPPER");
			int slot = guiConfig.getInt("Rongqi.Funnel.slot", 16);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				// 从 Variable 配置读取变量值
				String funnelStatus = isHopperEnabled ? 
					guiConfig.getString("Variable.FunnelstatusOn", "&a开") : 
					guiConfig.getString("Variable.FunnelstatusOFF", "&c关");
				funnelStatus = funnelStatus.replace('&', '§');
				ItemStack item = createGuiItemFromConfigWithVariable(guiConfig, "Rongqi.Funnel", material, "Funnelstatus", funnelStatus);
				gui.setItem(slot, item);
			}
		}
		
		// 密码箱按钮
		if (guiConfig.contains("Rongqi.Password")) {
			String materialStr = guiConfig.getString("Rongqi.Password.material", "PAPER");
			int slot = guiConfig.getInt("Rongqi.Password.slot", 20);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				// 从 Variable 配置读取变量值
				String passwordStatus = hasPassword ? 
					guiConfig.getString("Variable.PasswordOn", "&a已设置") : 
					guiConfig.getString("Variable.PasswordOFF", "&c未设置");
				passwordStatus = passwordStatus.replace('&', '§');
				ItemStack item = createGuiItemFromConfigWithVariable(guiConfig, "Rongqi.Password", material, "Password", passwordStatus);
				gui.setItem(slot, item);
			}
		}
		
		// 管理面板按钮
		if (guiConfig.contains("Rongqi.Manage")) {
			String materialStr = guiConfig.getString("Rongqi.Manage.material", "REPEATER");
			int slot = guiConfig.getInt("Rongqi.Manage.slot", 22);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "Rongqi.Manage", material);
				gui.setItem(slot, item);
			}
		}
		
		// 待开发功能
		if (guiConfig.contains("Rongqi.Development")) {
			String materialStr = guiConfig.getString("Rongqi.Development.material", "BARRIER");
			int slot = guiConfig.getInt("Rongqi.Development.slot", 24);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "Rongqi.Development", material);
				gui.setItem(slot, item);
			}
		}
		
		player.openInventory(gui);
	}
	
	/**
	 * 默认管理面板GUI（硬编码后备方案）
	 */
	private static void openManagementPanelGuiDefault(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Boolean> playerDefaultPublicSettings, Map<UUID, Boolean> playerDefaultHopperSettings) {
		Inventory gui = Bukkit.createInventory(null, MANAGEMENT_PANEL_ROWS * 9, "§b管理面板");
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 9; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 默认公开/私有设置按钮 (第2格，索引为1)
		UUID ownerUUID = player.getUniqueId();
		boolean isDefaultPublic = playerDefaultPublicSettings.getOrDefault(ownerUUID, false);
		String defaultStatus = isDefaultPublic ? "§a公开" : "§c私有";
		
		ItemStack book = createItem(Material.BOOK, "§b默认 §e公开/私有 §b设置",
			"",
			"§3私有 §8&l- §6你新放置的箱子默认为私有",
			"§3公开 §8&l- §6你新放置的箱子默认为公开",
			"",
			"§9当前默认状态: " + defaultStatus
		);
		gui.setItem(1, book);
		
		// 默认漏斗设置按钮 (第3格，索引为2)
		boolean isDefaultHopper = playerDefaultHopperSettings.getOrDefault(ownerUUID, false);
		String hopperStatus = isDefaultHopper ? "§a打开" : "§c关闭";
		
		ItemStack hopper = createItem(Material.HOPPER, "§b默认 §e漏斗开关 §b设置",
			"",
			"§3打开 §8&l- §6新放置的容器默认打开漏斗传输",
			"§3关闭 §8&l- §6新放置的容器默认关闭漏斗传输",
			"§8&l- §6默认漏斗开关",
			"",
			"§e当前状态: " + hopperStatus
		);
		gui.setItem(2, hopper);
		
		// 返回按钮 (最后格子，索引为8)
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§f返回",
			"",
			"§8点击返回上一级菜单"
		);
		gui.setItem(8, returnButton);
		
		player.openInventory(gui);
	}
	
	// 打开管理面板GUI (1行)
	public static void openManagementPanelGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Boolean> playerDefaultPublicSettings, Map<UUID, Boolean> playerDefaultHopperSettings) {
		// 如果配置文件未加载，使用默认硬编码
		if (guiConfig == null) {
			openManagementPanelGuiDefault(player, chestBlock, chestOwners, playerDefaultPublicSettings, playerDefaultHopperSettings);
			return;
		}
		
		// 从配置读取GUI设置
		String guiTitle = guiConfig.getString("ManagePanel.name", "&b管理面板");
		guiTitle = guiTitle.replace('&', '§');
		
		Inventory gui = Bukkit.createInventory(null, MANAGEMENT_PANEL_ROWS * 9, guiTitle);
		
		// 填充黑色玻璃
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < MANAGEMENT_PANEL_ROWS * 9; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 获取当前玩家的状态
		UUID ownerUUID = player.getUniqueId();
		boolean isDefaultPublic = playerDefaultPublicSettings.getOrDefault(ownerUUID, false);
		boolean isDefaultHopper = playerDefaultHopperSettings.getOrDefault(ownerUUID, false);
		
		// 从 Variable 配置读取变量值
		String defaultBoxStatus = isDefaultPublic ? 
			guiConfig.getString("Variable.DefaultBoxStatusOn", "&a公开") : 
			guiConfig.getString("Variable.DefaultBoxStatusOFF", "&c私有");
		defaultBoxStatus = defaultBoxStatus.replace('&', '§');
		
		String defaultFunnelStatus = isDefaultHopper ? 
			guiConfig.getString("Variable.DefaultFunnelOn", "&a打开") : 
			guiConfig.getString("Variable.DefaultFunnelOFF", "&c关闭");
		defaultFunnelStatus = defaultFunnelStatus.replace('&', '§');
		
		// 默认公开/私有设置按钮
		if (guiConfig.contains("ManagePanel.DeafultBox")) {
			String materialStr = guiConfig.getString("ManagePanel.DeafultBox.material", "BOOK");
			int slot = guiConfig.getInt("ManagePanel.DeafultBox.slot", 1);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfigWithVariable(guiConfig, "ManagePanel.DeafultBox", material, "DefaultBoxStatus", defaultBoxStatus);
				gui.setItem(slot, item);
			}
		}
		
		// 默认漏斗设置按钮
		if (guiConfig.contains("ManagePanel.DefaultFunnel")) {
			String materialStr = guiConfig.getString("ManagePanel.DefaultFunnel.material", "HOPPER");
			int slot = guiConfig.getInt("ManagePanel.DefaultFunnel.slot", 2);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfigWithVariable(guiConfig, "ManagePanel.DefaultFunnel", material, "DefaultFunnel", defaultFunnelStatus);
				gui.setItem(slot, item);
			}
		}
		
		// 返回按钮
		if (guiConfig.contains("ManagePanel.ManageBack")) {
			String materialStr = guiConfig.getString("ManagePanel.ManageBack.material", "WHITE_STAINED_GLASS_PANE");
			int slot = guiConfig.getInt("ManagePanel.ManageBack.slot", 8);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "ManagePanel.ManageBack", material);
				gui.setItem(slot, item);
			}
		}
		
		player.openInventory(gui);
	}
	
	/**
	 * 默认单独权限设置GUI（硬编码后备方案）
	 */
	private static void openSinglePermissionGuiDefault(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, SINGLE_ROWS * 9, "§b独立权限设置");
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack nameTag = createItem(Material.NAME_TAG, "§a设置权限",
			"",
			"§8&l- §6对单个容器的权限添加",
			"§8&l- §6点击玩家头颅后可添加权限",
			"§8&l- §6被添加权限的玩家可破坏你的容器"
		);
		gui.setItem(11, nameTag);
		
		ItemStack beacon = createItem(Material.BEACON, "§c取消权限",
			"",
			"§8&l- §6对单个容器的权限移除",
			"§8&l- §6点击玩家头颅后可移除权限"
		);
		gui.setItem(15, beacon);
		
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§f返回",
			"",
			"§8返回上一级菜单"
		);
		gui.setItem(26, returnButton);
		
		player.openInventory(gui);
	}
	
	// 打开单独权限设置GUI (3行)
	public static void openSinglePermissionGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		// 如果配置文件未加载，使用默认硬编码
		if (guiConfig == null) {
			openSinglePermissionGuiDefault(player, chestBlock, chestOwners);
			return;
		}
		
		// 从配置读取GUI设置
		String guiTitle = guiConfig.getString("IndPer.name", "&b单独权限设置");
		guiTitle = guiTitle.replace('&', '§');
		
		Inventory gui = Bukkit.createInventory(null, SINGLE_ROWS * 9, guiTitle);
		
		// 填充黑色玻璃
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < SINGLE_ROWS * 9; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 设置权限按钮
		if (guiConfig.contains("IndPer.SetPer")) {
			String materialStr = guiConfig.getString("IndPer.SetPer.material", "NAME_TAG");
			int slot = guiConfig.getInt("IndPer.SetPer.slot", 11);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "IndPer.SetPer", material);
				gui.setItem(slot, item);
			}
		}
		
		// 取消权限按钮
		if (guiConfig.contains("IndPer.DelPer")) {
			String materialStr = guiConfig.getString("IndPer.DelPer.material", "BEACON");
			int slot = guiConfig.getInt("IndPer.DelPer.slot", 15);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "IndPer.DelPer", material);
				gui.setItem(slot, item);
			}
		}
		
		// 返回按钮
		if (guiConfig.contains("IndPer.IndPerBack")) {
			String materialStr = guiConfig.getString("IndPer.IndPerBack.material", "WHITE_STAINED_GLASS_PANE");
			int slot = guiConfig.getInt("IndPer.IndPerBack.slot", 26);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "IndPer.IndPerBack", material);
				gui.setItem(slot, item);
			}
		}
		
		player.openInventory(gui);
	}
	
	/**
	 * 默认全局权限设置GUI（硬编码后备方案）
	 */
	private static void openGlobalPermissionGuiDefault(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, GLOBAL_ROWS * 9, "§b全局权限设置");
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack nameTag = createItem(Material.NAME_TAG, "§a添加全局权限",
			"",
			"§8&l- §6授权玩家可以打开你所有容器",
			"§8&l- §6但是依旧可以在单个容器取消权限",
			"§8&l- §6之后放置的也会自动授权权限",
			"§8&l- §6点击玩家头颅添加授权权限"
		);
		gui.setItem(11, nameTag);
		
		ItemStack beacon = createItem(Material.BEACON, "§c删除全局权限",
			"",
			"§8&l- §6移除玩家打开你所有容器权限",
			"§8&l- §6会移除之前单独授权的权限",
			"§8&l- §6点击玩家头颅后会移除权限"
		);
		gui.setItem(15, beacon);
		
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§f返回",
			"",
			"§8返回上一级菜单"
		);
		gui.setItem(26, returnButton);
		
		player.openInventory(gui);
	}
	
	// 打开全局权限设置GUI (3行)
	public static void openGlobalPermissionGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		// 如果配置文件未加载，使用默认硬编码
		if (guiConfig == null) {
			openGlobalPermissionGuiDefault(player, chestBlock, chestOwners);
			return;
		}
		
		// 从配置读取GUI设置
		String guiTitle = guiConfig.getString("GloPer.name", "&b全局权限设置");
		guiTitle = guiTitle.replace('&', '§');
		
		Inventory gui = Bukkit.createInventory(null, GLOBAL_ROWS * 9, guiTitle);
		
		// 填充黑色玻璃
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < GLOBAL_ROWS * 9; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 添加全局权限按钮
		if (guiConfig.contains("GloPer.SetGloPer")) {
			String materialStr = guiConfig.getString("GloPer.SetGloPer.material", "NAME_TAG");
			int slot = guiConfig.getInt("GloPer.SetGloPer.slot", 11);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "GloPer.SetGloPer", material);
				gui.setItem(slot, item);
			}
		}
		
		// 删除全局权限按钮
		if (guiConfig.contains("GloPer.DelGloPer")) {
			String materialStr = guiConfig.getString("GloPer.DelGloPer.material", "BEACON");
			int slot = guiConfig.getInt("GloPer.DelGloPer.slot", 15);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "GloPer.DelGloPer", material);
				gui.setItem(slot, item);
			}
		}
		
		// 返回按钮
		if (guiConfig.contains("GloPer.GloPerBack")) {
			String materialStr = guiConfig.getString("GloPer.GloPerBack.material", "WHITE_STAINED_GLASS_PANE");
			int slot = guiConfig.getInt("GloPer.GloPerBack.slot", 26);
			Material material = Material.matchMaterial(materialStr);
			if (material != null) {
				ItemStack item = createGuiItemFromConfig(guiConfig, "GloPer.GloPerBack", material);
				gui.setItem(slot, item);
			}
		}
		
		player.openInventory(gui);
	}
	
	// 打开添加全局权限 GUI (6行)
	public static void openGlobalAddGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_ADD_ROWS * 9, GLOBAL_ADD_TITLE);
		
		UUID ownerUUID = chestOwners.get(getLocationKey(chestBlock));
		Set<UUID> globallyAuthorized = globalPermissions.getOrDefault(ownerUUID, new HashSet<>());
		
		Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
		List<Player> playersWithoutGlobalPermission = new ArrayList<>();
		
		for (Player onlinePlayer : onlinePlayers) {
			if (onlinePlayer.getUniqueId().equals(ownerUUID)) {
				continue;
			}
			if (globallyAuthorized.contains(onlinePlayer.getUniqueId())) {
				continue;
			}
			playersWithoutGlobalPermission.add(onlinePlayer);
		}
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorder(gui, PERMISSION_ADD_ROWS, blackGlass);
		
		List<Integer> innerSlots = getInnerSlots(PERMISSION_ADD_ROWS);
		int playerIndex = 0;
		
		for (int slot : innerSlots) {
			if (playerIndex < playersWithoutGlobalPermission.size()) {
				Player targetPlayer = playersWithoutGlobalPermission.get(playerIndex);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§e");
				gui.setItem(slot, playerHead);
				playerIndex++;
			}
		}
		
		// 返回按钮
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(53, returnButton);
		
		player.openInventory(gui);
	}
	
	// 打开删除全局权限 GUI (6行，带分页)
	public static void openGlobalRemoveGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions, int page) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_REMOVE_ROWS * 9, GLOBAL_REMOVE_TITLE);
		
		UUID ownerUUID = chestOwners.get(getLocationKey(chestBlock));
		Set<UUID> globallyAuthorized = globalPermissions.getOrDefault(ownerUUID, new HashSet<>());
		
		List<OfflinePlayer> playersWithGlobalPermission = new ArrayList<>();
		for (UUID uuid : globallyAuthorized) {
			playersWithGlobalPermission.add(Bukkit.getOfflinePlayer(uuid));
		}
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorderPaginated(gui, PERMISSION_REMOVE_ROWS, blackGlass);
		
		int totalPages = Math.max(1, (int) Math.ceil((double) playersWithGlobalPermission.size() / PLAYERS_PER_PAGE));
		if (page < 0) page = 0;
		if (page >= totalPages) page = totalPages - 1;
		
		// 计算当前页的玩家范围
		int startIndex = page * PLAYERS_PER_PAGE;
		int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, playersWithGlobalPermission.size());
		
		// 获取内部格子（第2-5行，第2-8列 = 28格）
		List<Integer> innerSlots = getInnerSlotsPaginated(PERMISSION_REMOVE_ROWS);
		int slotIndex = 0;
		
		for (int i = startIndex; i < endIndex; i++) {
			if (slotIndex < innerSlots.size()) {
				OfflinePlayer targetPlayer = playersWithGlobalPermission.get(i);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§c");
				gui.setItem(innerSlots.get(slotIndex), playerHead);
				slotIndex++;
			}
		}
		
		// 下一页按钮 (黄绿色玻璃)
		if (page < totalPages - 1) {
			ItemStack nextButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§d下一页");
			gui.setItem(NEXT_PAGE_SLOT, nextButton);
		} else {
			ItemStack nextBlank = createItem(Material.LIME_STAINED_GLASS_PANE, "§d下一页");
			gui.setItem(NEXT_PAGE_SLOT, nextBlank);
		}
		
		// 上一页/返回按钮 (黄绿色玻璃, 第1页时返回上一级)
		if (page > 0) {
			ItemStack prevButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§d上一页");
			gui.setItem(PREV_PAGE_SLOT, prevButton);
		} else {
			ItemStack returnButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§d返回");
			gui.setItem(PREV_PAGE_SLOT, returnButton);
		}
		
		player.openInventory(gui);
	}
	
	// 没有权限的
	public static void openPermissionAddGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_ADD_ROWS * 9, PERMISSION_ADD_TITLE);
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.getOrDefault(locationKey, new HashSet<>());
		
		Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
		List<Player> playersWithoutPermission = new ArrayList<>();
		
		for (Player onlinePlayer : onlinePlayers) {
			UUID ownerUUID = chestOwners.get(locationKey);
			if (ownerUUID != null && onlinePlayer.getUniqueId().equals(ownerUUID)) {
				continue;
			}
			if (allowedPlayers.contains(onlinePlayer.getUniqueId())) {
				continue;
			}
			playersWithoutPermission.add(onlinePlayer);
		}
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorder(gui, PERMISSION_ADD_ROWS, blackGlass);
		
		List<Integer> innerSlots = getInnerSlots(PERMISSION_ADD_ROWS);
		int playerIndex = 0;
		
		for (int slot : innerSlots) {
			if (playerIndex < playersWithoutPermission.size()) {
				Player targetPlayer = playersWithoutPermission.get(playerIndex);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§6");
				// 添加 Lore
				ItemMeta meta = playerHead.getItemMeta();
				if (meta != null) {
					List<String> lore = new ArrayList<>();
					lore.add(" ");
					lore.add("§a点击添加独立权限");
					meta.setLore(lore);
					playerHead.setItemMeta(meta);
				}
				gui.setItem(slot, playerHead);
				playerIndex++;
			}
			
		}
		
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(53, returnButton);
		
		player.openInventory(gui);
	}
	
	// 移除权限 GUI (7行，带分页)
	public static void openPermissionRemoveGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions, int page) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_REMOVE_ROWS * 9, PERMISSION_REMOVE_TITLE);
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.getOrDefault(locationKey, new HashSet<>());
		
		List<OfflinePlayer> playersWithPermission = new ArrayList<>();
		for (UUID uuid : allowedPlayers) {
			playersWithPermission.add(Bukkit.getOfflinePlayer(uuid));
		}
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorderPaginated(gui, PERMISSION_REMOVE_ROWS, blackGlass);
		
		int totalPages = Math.max(1, (int) Math.ceil((double) playersWithPermission.size() / PLAYERS_PER_PAGE));
		if (page < 0) page = 0;
		if (page >= totalPages) page = totalPages - 1;
		
		// 计算当前页的玩家范围
		int startIndex = page * PLAYERS_PER_PAGE;
		int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, playersWithPermission.size());
		
		// 获取内部格子
		List<Integer> innerSlots = getInnerSlotsPaginated(PERMISSION_REMOVE_ROWS);
		int slotIndex = 0;
		
		for (int i = startIndex; i < endIndex; i++) {
			if (slotIndex < innerSlots.size()) {
				OfflinePlayer targetPlayer = playersWithPermission.get(i);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§c");
				gui.setItem(innerSlots.get(slotIndex), playerHead);
				slotIndex++;
			}
		}
		
		// 下一页按钮 (黄绿色玻璃)
		if (page < totalPages - 1) {
			ItemStack nextButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§d下一页");
			gui.setItem(NEXT_PAGE_SLOT, nextButton);
		} else {
			ItemStack nextBlank = createItem(Material.LIME_STAINED_GLASS_PANE, "§d下一页");
			gui.setItem(NEXT_PAGE_SLOT, nextBlank);
		}
		
		// 上一页按钮 (黄绿色玻璃, 第1页时返回上一级)
		if (page > 0) {
			ItemStack prevButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§d上一页");
			gui.setItem(PREV_PAGE_SLOT, prevButton);
		} else {
			ItemStack prevBlank = createItem(Material.LIME_STAINED_GLASS_PANE, "§d返回");
			gui.setItem(PREV_PAGE_SLOT, prevBlank);
		}
		
		player.openInventory(gui);
	}
	
	// 玻璃板（用于普通GUI）
	private static void fillBorder(Inventory gui, int rows, ItemStack blackGlass) {
		int cols = 9;
		
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				if (row == 0 || row == rows - 1 || col == 0 || col == cols - 1) {
					int slot = row * cols + col;
					gui.setItem(slot, blackGlass);
				}
			}
		}
	}
	
	// 玻璃板（用于分页GUI，排除底部导航区域）
	private static void fillBorderPaginated(Inventory gui, int rows, ItemStack blackGlass) {
		int cols = 9;
		
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int slot = row * cols + col;
				// 顶部边框 + 左右边框
				boolean isBorder = row == 0 || col == 0 || col == cols - 1;
				// 底部边框，但排除导航按钮位置
				boolean isBottomBorder = row == rows - 1 && slot != PREV_PAGE_SLOT && slot != NEXT_PAGE_SLOT;
				
				if (isBorder || isBottomBorder) {
					gui.setItem(slot, blackGlass);
				}
			}
		}
	}
	
	// 获取普通内部格子（6行用）
	private static List<Integer> getInnerSlots(int rows) {
		List<Integer> slots = new ArrayList<>();
		int cols = 9;
		
		for (int row = 1; row < rows - 1; row++) {
			for (int col = 1; col < cols - 1; col++) {
				slots.add(row * cols + col);
			}
		}
		return slots;
	}
	
	// 获取分页内部格子（6行用，排除最后一行导航区域）
	private static List<Integer> getInnerSlotsPaginated(int rows) {
		List<Integer> slots = new ArrayList<>();
		int cols = 9;
		// 只取第2-5行的内部格子，但排除导航按钮所在的格子 (4行 × 7列 = 28格，再减去导航格子)
		for (int row = 1; row < rows - 1; row++) {
			for (int col = 1; col < cols - 1; col++) {
				int slot = row * cols + col;
				// 排除导航按钮格子
				if (slot != PREV_PAGE_SLOT && slot != NEXT_PAGE_SLOT && slot != RETURN_SLOT) {
					slots.add(slot);
				}
			}
		}
		return slots;
	}
	
	// 判断Gui（动态从配置文件读取标题进行匹配）
	public static boolean isBoxManageGui(String title) {
		if (guiConfig != null && guiConfig.contains("Rongqi.name")) {
			String configTitle = guiConfig.getString("Rongqi.name").replace('&', '§');
			return configTitle.equals(title);
		}
		return BOX_MANAGE_TITLE.equals(title);
	}
	
	public static boolean isSinglePermissionGui(String title) {
		if (guiConfig != null && guiConfig.contains("IndPer.name")) {
			String configTitle = guiConfig.getString("IndPer.name").replace('&', '§');
			return configTitle.equals(title);
		}
		return SINGLE_PERMISSION_TITLE.equals(title);
	}
	
	public static boolean isPermissionAddGui(String title) {
		return PERMISSION_ADD_TITLE.equals(title);
	}
	
	public static boolean isPermissionRemoveGui(String title) {
		return PERMISSION_REMOVE_TITLE.equals(title);
	}
	
	public static boolean isGlobalPermissionGui(String title) {
		if (guiConfig != null && guiConfig.contains("GloPer.name")) {
			String configTitle = guiConfig.getString("GloPer.name").replace('&', '§');
			return configTitle.equals(title);
		}
		return GLOBAL_PERMISSION_TITLE.equals(title);
	}
	
	public static boolean isGlobalAddGui(String title) {
		return GLOBAL_ADD_TITLE.equals(title);
	}
	
	public static boolean isGlobalRemoveGui(String title) {
		return GLOBAL_REMOVE_TITLE.equals(title);
	}
	
	public static boolean isManagementPanelGui(String title) {
		if (guiConfig != null && guiConfig.contains("ManagePanel.name")) {
			String configTitle = guiConfig.getString("ManagePanel.name").replace('&', '§');
			return configTitle.equals(title);
		}
		return MANAGEMENT_PANEL_TITLE.equals(title);
	}
	public static void handleBoxManageClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions, Map<UUID, Set<UUID>> globalPermissions, Set<String> publicChests, Set<String> hopperEnabledChests, Map<String, String> chestPasswords, Map<UUID, Boolean> playerDefaultPublicSettings, Map<UUID, Boolean> playerDefaultHopperSettings) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return; // 在冷却中，忽略点击
		}
		
		switch (slot) {
			case 10: 
				openSinglePermissionGui(player, chestBlock, chestOwners);
				break;
			case 12: 
				openGlobalPermissionGui(player, chestBlock, chestOwners);
				break;
			case 14: {
				// 切换箱子公开/私有状态
				String locationKey = getLocationKey(chestBlock);
				if (publicChests.contains(locationKey)) {
					publicChests.remove(locationKey);
					MessageUtil.sendMessage(player, "LockDisable");
				} else {
					publicChests.add(locationKey);
					MessageUtil.sendMessage(player, "LockEnable");
				}
				// 刷新GUI
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				}, 2L);
				break;
			}
			case 16: {
				// 切换漏斗传输状态
				String locationKey = getLocationKey(chestBlock);
				if (hopperEnabledChests.contains(locationKey)) {
					hopperEnabledChests.remove(locationKey);
					MessageUtil.sendMessage(player, "FunnelDisable");
				} else {
					hopperEnabledChests.add(locationKey);
					MessageUtil.sendMessage(player, "FunnelEnable");
				}
				// 刷新GUI
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				}, 2L);
				break;
			}
			case 20: {
				// 密码箱设置/清除
				String locationKey = getLocationKey(chestBlock);
				if (chestPasswords.containsKey(locationKey)) {
					// 已有密码，清除密码
					chestPasswords.remove(locationKey);
					MessageUtil.sendMessage(player, "PasswordBoxClear");
					// 刷新GUI
					Bukkit.getScheduler().runTaskLater(plugin, () -> {
						openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
					}, 2L);
				} else {
					// 没有密码，进入等待输入状态
					MessageUtil.sendMessage(player, "PasswordBoxSet");
								
					// 标记玩家正在设置密码并记录对应的箱子位置
					player.closeInventory();
					mian.xlingran.Shan.getInstance().setPasswordInputState(player.getUniqueId(), locationKey);
				}
				break;
			}
			case 22: {
				// 管理面板按钮
				openManagementPanelGui(player, chestBlock, chestOwners, playerDefaultPublicSettings, playerDefaultHopperSettings);
				break;
			}
		}
	}
	
	// 管理面板GUI点击
	public static void handleManagementPanelClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Boolean> playerDefaultPublicSettings, Map<UUID, Boolean> playerDefaultHopperSettings) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return; // 在冷却中，忽略点击
		}
		
		UUID ownerUUID = player.getUniqueId();
		
		if (slot == 1) {
			// 切换默认公开/私有设置
			boolean currentDefault = playerDefaultPublicSettings.getOrDefault(ownerUUID, false);
			boolean newDefault = !currentDefault;
			playerDefaultPublicSettings.put(ownerUUID, newDefault);
			MessageUtil.sendMessage(player, newDefault ? "DefaultPlaceDisable" : "DefaultPlaceEnable");
			// 刷新管理面板GUI
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				openManagementPanelGui(player, chestBlock, chestOwners, playerDefaultPublicSettings, playerDefaultHopperSettings);
			}, 2L);
		} else if (slot == 2) {
			// 切换默认漏斗设置
			boolean currentHopper = playerDefaultHopperSettings.getOrDefault(ownerUUID, false);
			boolean newHopper = !currentHopper;
			playerDefaultHopperSettings.put(ownerUUID, newHopper);
			MessageUtil.sendMessage(player, newHopper ? "DefaultFunnelEnable" : "DefaultFunnelDisable");
			// 刷新管理面板GUI
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				openManagementPanelGui(player, chestBlock, chestOwners, playerDefaultPublicSettings, playerDefaultHopperSettings);
			}, 2L);
		}
	}
	
	// 单独权限设置GUI点击
	public static void handleSinglePermissionClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions, Set<String> publicChests, Set<String> hopperEnabledChests, Map<String, String> chestPasswords, Map<UUID, Boolean> playerDefaultPublicSettings) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return; // 在冷却中，忽略点击
		}
		
		switch (slot) {
			case 11: 
				openPermissionAddGui(player, chestBlock, chestOwners, chestPermissions);
				break;
			case 15: 
				openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, 0);
				break;
			case 26: 
				openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				break;
		}
	}
	
	// 全局权限设置GUI点击
	public static void handleGlobalPermissionClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions, Set<String> publicChests, Set<String> hopperEnabledChests, Map<String, String> chestPasswords, Map<UUID, Boolean> playerDefaultPublicSettings) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return; // 在冷却中，忽略点击
		}
		
		switch (slot) {
			case 11: 
				openGlobalAddGui(player, chestBlock, chestOwners, globalPermissions);
				break;
			case 15: 
				openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, 0);
				break;
			case 26: 
				openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				break;
		}
	}
	
	// 添加全局权限
	public static boolean handleGlobalAddClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions, Map<String, Set<UUID>> chestPermissions, Set<UUID> switchingGuiPlayers) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return false; // 在冷却中，忽略点击
		}
		
		UUID ownerUUID = chestOwners.get(getLocationKey(chestBlock));
		if (ownerUUID == null) {
			return false;
		}
		
		// 返回按钮
		if (slot == 53) {
			switchingGuiPlayers.add(player.getUniqueId());
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				openGlobalPermissionGui(player, chestBlock, chestOwners);
				switchingGuiPlayers.remove(player.getUniqueId());
			}, 2L);
			return false;
		}
		
		Set<UUID> authorizedPlayers = globalPermissions.computeIfAbsent(ownerUUID, k -> new HashSet<>());
		
		if (isInnerSlot(slot, PERMISSION_ADD_ROWS)) {
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§e", "");
					
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
						authorizedPlayers.add(targetPlayer.getUniqueId());
						
						// 同时给当前玩家所有箱子添加权限
						for (Map.Entry<String, Set<UUID>> entry : chestPermissions.entrySet()) {
							String locKey = entry.getKey();
							UUID chestOwner = chestOwners.get(locKey);
							if (chestOwner != null && chestOwner.equals(ownerUUID)) {
								entry.getValue().add(targetPlayer.getUniqueId());
							}
						}
						
						Map<String, String> vars = Map.of("player", playerName);
						MessageUtil.sendMessage(player, "Globaladd", vars);
						player.closeInventory();
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// 删除全局权限
	public static boolean handleGlobalRemoveClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions, Map<String, Set<UUID>> chestPermissions, Map<UUID, Integer> playerGuiPages, Set<UUID> switchingGuiPlayers, int currentPage) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return false; // 在冷却中，忽略点击
		}
		
		UUID ownerUUID = chestOwners.get(getLocationKey(chestBlock));
		if (ownerUUID == null) {
			return false;
		}
		
		Set<UUID> authorizedPlayers = globalPermissions.get(ownerUUID);
		
		// 先处理导航按钮，确保即使列表为空也能返回
		// 上一页/返回 (第1页时返回上一级, 其他页时返回上一页)
		if (slot == PREV_PAGE_SLOT) {
			if (currentPage == 0) {
				// 返回上一级GUI
				playerGuiPages.remove(player.getUniqueId());
				switchingGuiPlayers.add(player.getUniqueId());
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					openGlobalPermissionGui(player, chestBlock, chestOwners);
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			} else {
				int newPage = currentPage - 1;
				playerGuiPages.put(player.getUniqueId(), newPage);
				switchingGuiPlayers.add(player.getUniqueId());
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, newPage);
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			}
			return false;
		}
		
		// 下一页
		if (slot == NEXT_PAGE_SLOT) {
			int totalPages = Math.max(1, (int) Math.ceil((double) (authorizedPlayers != null ? authorizedPlayers.size() : 0) / PLAYERS_PER_PAGE));
			int newPage = Math.min(totalPages - 1, currentPage + 1);
			playerGuiPages.put(player.getUniqueId(), newPage);
			switchingGuiPlayers.add(player.getUniqueId());
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, newPage);
				switchingGuiPlayers.remove(player.getUniqueId());
			}, 2L);
			return false;
		}
		
		// 检查是否有权限数据
		if (authorizedPlayers == null || authorizedPlayers.isEmpty()) {
			return false;
		}
		
		if (isInnerSlotPaginated(slot, PERMISSION_REMOVE_ROWS)) {
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§c", "");
					
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
						UUID targetUUID = targetPlayer.getUniqueId();
						authorizedPlayers.remove(targetUUID);
						
						// 从所有箱子中移除权限
						for (Map.Entry<String, Set<UUID>> entry : chestPermissions.entrySet()) {
							String locKey = entry.getKey();
							UUID chestOwner = chestOwners.get(locKey);
							if (chestOwner != null && chestOwner.equals(ownerUUID)) {
								entry.getValue().remove(targetUUID);
							}
						}
						
						if (authorizedPlayers.isEmpty()) {
							globalPermissions.remove(ownerUUID);
						}
						
						Map<String, String> vars = Map.of("player", playerName);
						MessageUtil.sendMessage(player, "GlobalRemove", vars);
						
						// 刷新当前页面
						int totalPages = Math.max(1, (int) Math.ceil((double) authorizedPlayers.size() / PLAYERS_PER_PAGE));
						int page = Math.min(currentPage, totalPages - 1);
						playerGuiPages.put(player.getUniqueId(), page);
						switchingGuiPlayers.add(player.getUniqueId());
						Bukkit.getScheduler().runTaskLater(plugin, () -> {
							openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, page);
							switchingGuiPlayers.remove(player.getUniqueId());
						}, 2L);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// 添加权限
	public static boolean handlePermissionAddClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return false; // 在冷却中，忽略点击
		}
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>());
		
		if (slot == 53) {
			openSinglePermissionGui(player, chestBlock, chestOwners);
			return false;
		}
		
		if (isInnerSlot(slot, PERMISSION_ADD_ROWS)) {
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§6", "");
					
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
						allowedPlayers.add(targetPlayer.getUniqueId());
						Map<String, String> vars = Map.of("player", playerName);
						MessageUtil.sendMessage(player, "Individualadd", vars);
						player.closeInventory();
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// 移除权限
	public static boolean handlePermissionRemoveClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions, Map<UUID, Integer> playerGuiPages, Set<UUID> switchingGuiPlayers, int currentPage) {
		// 检查点击冷却
		if (isOnCooldown(player)) {
			return false; // 在冷却中，忽略点击
		}
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
		
		// 先处理导航按钮，确保即使没有权限数据也能返回
		// 上一页/返回 (第1页时返回上一级, 其他页时返回上一页)
		if (slot == PREV_PAGE_SLOT) {
			if (currentPage == 0) {
				// 返回上一级GUI
				playerGuiPages.remove(player.getUniqueId());
				switchingGuiPlayers.add(player.getUniqueId());
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					openSinglePermissionGui(player, chestBlock, chestOwners);
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			} else {
				switchingGuiPlayers.add(player.getUniqueId());
				int newPage = currentPage - 1;
				playerGuiPages.put(player.getUniqueId(), newPage);
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, newPage);
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			}
			return false;
		}
		
		// 下一页
		if (slot == NEXT_PAGE_SLOT) {
			int totalPages = Math.max(1, (int) Math.ceil((double) (allowedPlayers != null ? allowedPlayers.size() : 0) / PLAYERS_PER_PAGE));
			int newPage = Math.min(totalPages - 1, currentPage + 1);
			playerGuiPages.put(player.getUniqueId(), newPage);
			switchingGuiPlayers.add(player.getUniqueId());
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, newPage);
				switchingGuiPlayers.remove(player.getUniqueId());
			}, 2L);
			return false;
		}
		
		// 检查是否有权限数据
		if (allowedPlayers == null || allowedPlayers.isEmpty()) {
			return false;
		}
		
		if (isInnerSlotPaginated(slot, PERMISSION_REMOVE_ROWS)) {
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§c", "");
					
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
						allowedPlayers.remove(targetPlayer.getUniqueId());
						Map<String, String> vars = Map.of("player", playerName);
						MessageUtil.sendMessage(player, "IndividualRemove", vars);
						
						// 刷新当前页面
						int totalPages = Math.max(1, (int) Math.ceil((double) allowedPlayers.size() / PLAYERS_PER_PAGE));
						int page = Math.min(currentPage, totalPages - 1);
						playerGuiPages.put(player.getUniqueId(), page);
						switchingGuiPlayers.add(player.getUniqueId());
						Bukkit.getScheduler().runTaskLater(plugin, () -> {
							openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, page);
							switchingGuiPlayers.remove(player.getUniqueId());
						}, 2L);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// 判断普通内部区域
	private static boolean isInnerSlot(int slot, int rows) {
		int row = slot / 9;
		int col = slot % 9;
		return row >= 1 && row < rows - 1 && col >= 1 && col <= 7;
	}
	
	// 判断分页内部区域（排除分页按钮格子）
	private static boolean isInnerSlotPaginated(int slot, int rows) {
		if (slot == PREV_PAGE_SLOT || slot == NEXT_PAGE_SLOT || slot == RETURN_SLOT) {
			return false;
		}
		int row = slot / 9;
		int col = slot % 9;
		return row >= 1 && row < rows - 1 && col >= 1 && col <= 7;
	}
	
	// 创建物品
	private static ItemStack createItem(Material material, String name, String... lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			if (name != null) {
				meta.setDisplayName(name);
			}
			if (lore != null && lore.length > 0) {
				List<String> loreList = new ArrayList<>();
				for (String line : lore) {
					loreList.add(line);
				}
				meta.setLore(loreList);
			}
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			item.setItemMeta(meta);
		}
		return item;
	}
	
	// 创建头
	private static ItemStack createPlayerHead(Block chestBlock, Map<String, UUID> chestOwners) {
		String locationKey = getLocationKey(chestBlock);
		UUID ownerUUID = chestOwners.get(locationKey);
		
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		
		if (meta != null) {
			meta.setDisplayName("§e单独权限");
			
			// 添加 Lore 描述
			List<String> lore = new ArrayList<>();
			lore.add("§8§l- §6给予玩家可以打开该箱子");
			lore.add("§8§l- §6对单个箱子权限设置");
			meta.setLore(lore);
			
			if (ownerUUID != null) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUUID);
				meta.setOwningPlayer(offlinePlayer);
			}
			
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			head.setItemMeta(meta);
		}
		
		return head;
	}
	
	private static ItemStack createPermissionPlayerHead(Player player, String colorCode) {
		return ShanMeta.createCachedPlayerHead(player, colorCode + player.getName());
	}
	
	private static ItemStack createPermissionPlayerHead(OfflinePlayer player, String colorCode) {
		return ShanMeta.createCachedPlayerHead(player, colorCode + player.getName());
	}
	
	// 位置
	private static String getLocationKey(Block block) {
		return block.getWorld().getName() + ":" + 
		       block.getX() + "," + 
		       block.getY() + "," + 
		       block.getZ();
	}
}
