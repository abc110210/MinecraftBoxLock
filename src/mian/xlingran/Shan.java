package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;

public class Shan extends JavaPlugin implements Listener {
	
	private static Shan instance;
	
	public static Shan getInstance() {
		return instance;
	}
	
	// 静态初始化块：在类加载时强制设置 UTF-8 编码，解决 Windows CMD 乱码
	static {
		try {
			System.setProperty("file.encoding", "UTF-8");
			System.setProperty("sun.jnu.encoding", "UTF-8");
			
			// 尝试修改控制台 Handler 的编码
			java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
			for (Handler handler : rootLogger.getHandlers()) {
				if (handler instanceof ConsoleHandler) {
					handler.setEncoding("UTF-8");
				}
			}
		} catch (Exception e) {
			// 忽略编码设置失败
		}
	}
	
	// 储存箱
	private Map<String, UUID> chestOwners = new HashMap<>();
	private Map<String, Set<UUID>> chestPermissions = new HashMap<>();
	private Map<UUID, Set<UUID>> globalPermissions = new HashMap<>();
	private Set<String> publicChests = new HashSet<>(); // 公开的箱子（全服可打开但无法破坏）
	private Set<String> hopperEnabledChests = new HashSet<>(); // 允许漏斗传输的箱子
	private Map<String, String> chestPasswords = new HashMap<>(); // 密码箱的密码存储
	private Set<UUID> waitingForPasswordSet = new HashSet<>(); // 等待设置密码的玩家（主人）
	private Set<UUID> waitingForPasswordOpen = new HashSet<>(); // 等待输入密码打开箱子的玩家（路人）
	private Map<UUID, String> playerPendingPasswordChests = new HashMap<>(); // 记录玩家正在操作密码的箱子位置
	private Map<UUID, Map.Entry<String, String>> verifiedPasswordChests = new HashMap<>(); // 已通过密码验证的玩家（临时权限）：存储 箱子位置 和 验证时的密码
	private Map<UUID, Boolean> playerDefaultPublicSettings = new HashMap<>(); // 玩家默认新箱子公开/私有设置：true=公开, false/null=私有
	private File dataFile;
	
	private Set<UUID> guiOpeningPlayers = new HashSet<>();
	private Map<UUID, String> playerOpenedChests = new HashMap<>();
	private Set<UUID> switchingGuiPlayers = new HashSet<>();
	private Map<UUID, Integer> playerGuiPages = new HashMap<>();
	
	@Override
	public void onEnable() {
		instance = this;
		ShanGui.setPlugin(this);
		ShanMeta.init(this); // 初始化头颅缓存系统
		Bukkit.getPluginManager().registerEvents(this, this);
		
		// 注册指令
		this.getCommand("xlr").setExecutor(this);
		this.getCommand("xlrdel").setExecutor(this);
		
		// 初始化
		dataFile = new File(getDataFolder(), "chests.yml");
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		
		// 加载数据
		loadChestData();
		
		// 使用 Logger 输出，去掉 § 颜色代码避免 CMD 编码冲突
		getLogger().info("欢迎使用 箱子锁 插件,交流群: 943446220");
	}
	
	@Override
	public void onDisable() {
		// 保存
		saveChestData();
		// 使用 Logger 输出，去掉 § 颜色代码避免 CMD 编码冲突
		getLogger().info("箱子锁插件已关闭，交流群: 943446220");
	}
	
	/**
	 * 设置控制台编码，解决 Windows 乱码问题
	 * @param message 要输出的消息（会自动移除 § 颜色代码）
	 */
	private void logInfo(String message) {
		// 移除颜色代码，避免 CMD 编码冲突
		String cleanMessage = message.replace("§a", "")
		                              .replace("§b", "")
		                              .replace("§c", "")
		                              .replace("§d", "")
		                              .replace("§e", "")
		                              .replace("§f", "")
		                              .replace("§6", "")
		                              .replace("§8", "")
		                              .replace("§9", "")
		                              .replace("§r", "")
		                              .replace("§l", "");
		getLogger().info(cleanMessage);
	}
	
	//监听防止事件
	@EventHandler
	public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
		// 新的头颅缓存系统使用定时任务自动缓存，无需手动预缓存
	}

	// 监听聊天输入密码（设置密码 - 主人）
	@EventHandler
	public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		if (!waitingForPasswordSet.contains(player.getUniqueId())) {
			return;
		}

		event.setCancelled(true); // 阻止密码显示在聊天栏
		String message = event.getMessage().trim();
		String chestLocation = playerPendingPasswordChests.get(player.getUniqueId());

		if (chestLocation == null) {
			waitingForPasswordSet.remove(player.getUniqueId());
			return;
		}

		if (message.equalsIgnoreCase("quit")) {
			waitingForPasswordSet.remove(player.getUniqueId());
			playerPendingPasswordChests.remove(player.getUniqueId());
			player.sendMessage("§a已取消设置密码");
			return;
		}

		// 验证密码格式：4~8 位，仅英文和数字
		if (!message.matches("[a-zA-Z0-9]{4,8}")) {
			player.sendMessage("§c密码长度必须在 4~8 位之间，且仅支持英文和数字，请重新输入");
			player.sendMessage("§a输入 §equit §a取消设置");
			return;
		}

		// 设置成功
		chestPasswords.put(chestLocation, message);
		saveChestData();
		waitingForPasswordSet.remove(player.getUniqueId());
		playerPendingPasswordChests.remove(player.getUniqueId());
		player.sendMessage("§a密码箱设置成功！");

		// 刷新 GUI (异步事件需切回主线程)
		Bukkit.getScheduler().runTask(this, () -> {
			Block chestBlock = parseBlockLocation(player, chestLocation);
			if (chestBlock != null) {
				ShanGui.openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
			}
		});
	}

	// 监听打开密码箱时的密码输入（路人）
	@EventHandler
	public void onPlayerOpenPasswordChest(org.bukkit.event.player.AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		if (!waitingForPasswordOpen.contains(player.getUniqueId())) {
			return;
		}

		event.setCancelled(true); // 阻止密码显示在聊天栏
		String message = event.getMessage().trim();
		String chestLocation = playerPendingPasswordChests.get(player.getUniqueId());

		if (chestLocation == null) {
			waitingForPasswordOpen.remove(player.getUniqueId());
			return;
		}

		if (message.equalsIgnoreCase("quit")) {
			waitingForPasswordOpen.remove(player.getUniqueId());
			playerPendingPasswordChests.remove(player.getUniqueId());
			player.sendMessage("§a已取消打开箱子");
			return;
		}

		// 验证密码
		String correctPassword = chestPasswords.get(chestLocation);
		if (correctPassword != null && message.equals(correctPassword)) {
			// 密码正确，给予临时打开权限（存储箱子位置和验证时的密码）
			verifiedPasswordChests.put(player.getUniqueId(), new AbstractMap.SimpleEntry<>(chestLocation, correctPassword));
			waitingForPasswordOpen.remove(player.getUniqueId());
			playerPendingPasswordChests.remove(player.getUniqueId());
			player.sendMessage("§a密码正确，请再次右键点击箱子打开");
		} else {
			player.sendMessage("§c密码错误，请重新输入（输入 quit 取消打开）");
		}
	}

	/**
	 * 设置玩家等待输入密码的状态（主人设置密码）
	 */
	public void setPasswordInputState(UUID playerUUID, String locationKey) {
		waitingForPasswordSet.add(playerUUID);
		playerPendingPasswordChests.put(playerUUID, locationKey);
	}
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		Block block = event.getBlock();
		Player player = event.getPlayer();
		
		if (isChest(block.getType())) {
			String locationKey = getLocationKey(block);
			UUID ownerUUID = player.getUniqueId();
			chestOwners.put(locationKey, ownerUUID);
			
			// 自动应用全局授权 - 将所有者已授权的所有玩家添加到此新箱子
			Set<UUID> globallyAuthorized = globalPermissions.get(ownerUUID);
			if (globallyAuthorized != null && !globallyAuthorized.isEmpty()) {
				Set<UUID> chestPerms = chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>());
				chestPerms.addAll(globallyAuthorized);
			}
			
			// 应用玩家的默认公开/私有设置
			Boolean defaultPublic = playerDefaultPublicSettings.get(ownerUUID);
			if (defaultPublic != null && defaultPublic) {
				// 如果玩家设置为默认公开，则新箱子自动设为公开
				publicChests.add(locationKey);
			}
			
			saveChestData();
		}
	}
	

	// 判断玩家是否可以打开
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteractRight(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		
		Block block = event.getClickedBlock();
		if (block == null || !isChest(block.getType())) {
			return;
		}
		
		Player player = event.getPlayer();
		String locationKey = getLocationKey(block);
		UUID ownerUUID = chestOwners.get(locationKey);
		
		if (ownerUUID == null) {
			return;
		}
		
		// 箱子主人直接允许打开（密码箱对主人无效）
		if (ownerUUID.equals(player.getUniqueId())) {
			return;
		}
		
		// 密码箱优先：如果箱子设置了密码
		if (chestPasswords.containsKey(locationKey)) {
			String currentPassword = chestPasswords.get(locationKey);
			
			// 检查玩家是否已经通过密码验证
			Map.Entry<String, String> verifiedData = verifiedPasswordChests.get(player.getUniqueId());
			boolean isPasswordModified = false;
			if (verifiedData != null && verifiedData.getKey().equals(locationKey)) {
				// 玩家之前验证过这个箱子，检查密码是否被修改
				if (verifiedData.getValue().equals(currentPassword)) {
					// 密码未修改，允许打开
					return;
				} else {
					// 密码已修改，清除旧验证状态并标记
					verifiedPasswordChests.remove(player.getUniqueId());
					isPasswordModified = true;
				}
			}
							
			// 未通过密码验证或密码已修改，拦截并提示输入密码
			event.setCancelled(true);
							
			// 判断是否是密码修改后的第一次打开
			if (isPasswordModified) {
				player.sendMessage("§c密码已被修改，请重新输入密码（输入 quit 取消打开）");
			} else {
				player.sendMessage("§c该箱子已启用密码保护，请在聊天栏输入密码（输入 quit 取消打开）");
			}
					
			// 进入等待输入密码状态（路人打开箱子）
			waitingForPasswordOpen.add(player.getUniqueId());
			playerPendingPasswordChests.put(player.getUniqueId(), locationKey);
			return;
		}
		
		// 非密码箱，走原有权限检查逻辑
		if (!hasChestPermission(locationKey, player)) {
			event.setCancelled(true);
			player.sendMessage("§c这个箱子已被锁定，您无法打开！");
		}
	}

	// 快捷打开Gui界面
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteractLeft(PlayerInteractEvent event) {
		if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
			return;
		}
		
		Block block = event.getClickedBlock();
		if (block == null || !isChest(block.getType())) {
			return;
		}
		
		Player player = event.getPlayer();
		String locationKey = getLocationKey(block);
		UUID ownerUUID = chestOwners.get(locationKey);
		
		if (ownerUUID == null) {
			return;
		}
		
		if (ownerUUID.equals(player.getUniqueId()) && player.isSneaking()) {
			event.setCancelled(true);
			openGui(player, block);
		}
	}
	
	// 破坏事件
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		Player player = event.getPlayer();
		
		if (guiOpeningPlayers.contains(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}
		
		if (!isChest(block.getType())) {
			return;
		}
		
		String locationKey = getLocationKey(block);
		UUID ownerUUID = chestOwners.get(locationKey);
		
		if (ownerUUID != null) {
			// 公开箱子：只有所有者可以破坏
			if (publicChests.contains(locationKey) && !ownerUUID.equals(player.getUniqueId())) {
				event.setCancelled(true);
				player.sendMessage("§c这个箱子是公开的，您无法破坏！");
				return;
			}
			
			// 私有箱子：无权限的玩家无法破坏
			if (!hasChestPermission(locationKey, player)) {
				event.setCancelled(true);
				player.sendMessage("§c这个箱子已被锁定，您无法破坏！");
				return;
			}
			
			// 有权限的破坏：清除数据
			chestOwners.remove(locationKey);
			chestPermissions.remove(locationKey);
			publicChests.remove(locationKey);
			hopperEnabledChests.remove(locationKey);
			chestPasswords.remove(locationKey);
			saveChestData();
		}
	}
	
	// 漏斗相关
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryMoveItem(InventoryMoveItemEvent event) {
		if (isLockedChest(event.getSource())) {
			event.setCancelled(true);
			return;
		}
		
		if (isLockedChest(event.getDestination())) {
			event.setCancelled(true);
		}
	}
	
	// 判断是否上锁
	private boolean isLockedChest(Inventory inventory) {
		if (inventory == null || inventory.getHolder() == null) {
			return false;
		}
		
		org.bukkit.block.Block block = null;
		try {
			Object holder = inventory.getHolder();
			if (holder instanceof org.bukkit.block.BlockState) {
				block = ((org.bukkit.block.BlockState) holder).getBlock();
			} else if (holder instanceof org.bukkit.block.DoubleChest) {
				block = ((org.bukkit.block.DoubleChest) holder).getLocation().getBlock();
			}
		} catch (Exception e) {
			return false;
		}
		
		if (block == null) {
			return false;
		}
		
		if (!isChest(block.getType())) {
			return false;
		}
		
		String locationKey = getLocationKey(block);
		// 如果没有上锁，或者开启了漏斗传输，则允许传输
		if (!chestOwners.containsKey(locationKey)) {
			return false;
		}
		if (hopperEnabledChests.contains(locationKey)) {
			return false;
		}
		return true;
	}
	
		@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getView() == null) {
			return;
		}
		
		String title = event.getView().getTitle();
		Player player = (Player) event.getWhoClicked();
		int slot = event.getRawSlot();
		String chestLocation = playerOpenedChests.get(player.getUniqueId());
		
		if (chestLocation == null) {
			return;
		}
		
		Block chestBlock = parseBlockLocation(player, chestLocation);
		if (chestBlock == null) {
			return;
		}
		
		if (ShanGui.isBoxManageGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			final String loc = chestLocation;
			final int s = slot;
			Bukkit.getScheduler().runTaskLater(this, () -> {
				Block cb = parseBlockLocation(player, loc);
				if (cb != null) {
					ShanGui.handleBoxManageClick(player, s, cb, chestOwners, chestPermissions, globalPermissions, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				}
			}, 2L);
		}
		else if (ShanGui.isManagementPanelGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			final String loc = chestLocation;
			final int s = slot;
			Bukkit.getScheduler().runTaskLater(this, () -> {
				Block cb = parseBlockLocation(player, loc);
				if (cb != null) {
					ShanGui.handleManagementPanelClick(player, s, cb, chestOwners, playerDefaultPublicSettings);
				}
			}, 2L);
		}
		else if (ShanGui.isSinglePermissionGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			final String loc = chestLocation;
			final int s = slot;
			Bukkit.getScheduler().runTaskLater(this, () -> {
				Block cb = parseBlockLocation(player, loc);
				if (cb != null) {
					ShanGui.handleSinglePermissionClick(player, s, cb, chestOwners, chestPermissions, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				}
			}, 2L);
		}
		else if (ShanGui.isGlobalPermissionGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			final String loc = chestLocation;
			final int s = slot;
			Bukkit.getScheduler().runTaskLater(this, () -> {
				Block cb = parseBlockLocation(player, loc);
				if (cb != null) {
					ShanGui.handleGlobalPermissionClick(player, s, cb, chestOwners, globalPermissions, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
				}
			}, 2L);
		}
		else if (ShanGui.isGlobalAddGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			boolean closed = ShanGui.handleGlobalAddClick(player, slot, chestBlock, chestOwners, globalPermissions, chestPermissions, switchingGuiPlayers);
			if (closed) {
				saveChestData();
			}
		}
		else if (ShanGui.isGlobalRemoveGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			int currentPage = playerGuiPages.getOrDefault(player.getUniqueId(), 0);
			boolean closed = ShanGui.handleGlobalRemoveClick(player, slot, chestBlock, chestOwners, globalPermissions, chestPermissions, playerGuiPages, switchingGuiPlayers, currentPage);
			if (closed) {
				saveChestData();
			}
		}
		else if (ShanGui.isPermissionAddGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			if (slot == 53) {
				final String loc = chestLocation;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.openSinglePermissionGui(player, cb, chestOwners);
					}
				}, 2L);
			} else {
				boolean closed = ShanGui.handlePermissionAddClick(player, slot, chestBlock, chestOwners, chestPermissions);
				if (closed) {
					saveChestData();
				}
			}
		}
		else if (ShanGui.isPermissionRemoveGui(title)) {
			event.setCancelled(true);
			switchingGuiPlayers.add(player.getUniqueId());
			int currentPage = playerGuiPages.getOrDefault(player.getUniqueId(), 0);
			boolean closed = ShanGui.handlePermissionRemoveClick(player, slot, chestBlock, chestOwners, chestPermissions, playerGuiPages, switchingGuiPlayers, currentPage);
			if (closed) {
				saveChestData();
			}
		}
	}

	// 关闭事件
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (event.getView() == null) {
			return;
		}
		
		String title = event.getView().getTitle();
		if (!ShanGui.isBoxManageGui(title) && 
			!ShanGui.isSinglePermissionGui(title) && 
			!ShanGui.isPermissionAddGui(title) && 
			!ShanGui.isPermissionRemoveGui(title) &&
			!ShanGui.isGlobalPermissionGui(title) &&
			!ShanGui.isGlobalAddGui(title) &&
			!ShanGui.isGlobalRemoveGui(title)) {
			return;
		}
		
		Player player = (Player) event.getPlayer();
		
		// 正在切换 GUI 时：清除切换标记并跳过状态清理
		if (switchingGuiPlayers.remove(player.getUniqueId())) {
			return;
		}
		
		playerOpenedChests.remove(player.getUniqueId());
	}
	
	private void openGui(Player player, Block chestBlock) {
		guiOpeningPlayers.add(player.getUniqueId());
		playerOpenedChests.put(player.getUniqueId(), getLocationKey(chestBlock));
		
		Bukkit.getScheduler().runTaskLater(this, () -> {
			guiOpeningPlayers.remove(player.getUniqueId());
		}, 1L);
		
		ShanGui.openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
	}
	
	// 字符串 Block
	private Block parseBlockLocation(Player player, String locationKey) {
		String[] parts = locationKey.split(":");
		if (parts.length != 2) {
			return null;
		}
		
		org.bukkit.World world = Bukkit.getWorld(parts[0]);
		if (world == null) {
			return null;
		}
		
		String[] coords = parts[1].split(",");
		if (coords.length != 3) {
			return null;
		}
		
		try {
			int x = Integer.parseInt(coords[0]);
			int y = Integer.parseInt(coords[1]);
			int z = Integer.parseInt(coords[2]);
			return world.getBlockAt(x, y, z);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	// 箱子类型
	private boolean isChest(Material material) {
		return material == Material.CHEST || 
		       material == Material.TRAPPED_CHEST;
	}
	
	//箱子位置
	private String getLocationKey(Block block) {
		return block.getWorld().getName() + ":" + 
		       block.getX() + "," + 
		       block.getY() + "," + 
		       block.getZ();
	}
	
	// 写入到配置文件
	private void saveChestData() {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
			for (Map.Entry<String, UUID> entry : chestOwners.entrySet()) {
				writer.write("owner:" + entry.getKey() + "=" + entry.getValue().toString());
				writer.newLine();
			}
			for (Map.Entry<String, Set<UUID>> entry : chestPermissions.entrySet()) {
				String locationKey = entry.getKey();
				for (UUID uuid : entry.getValue()) {
					writer.write("perm:" + locationKey + "=" + uuid.toString());
					writer.newLine();
				}
			}
			// 保存全局授权数据
			for (Map.Entry<UUID, Set<UUID>> entry : globalPermissions.entrySet()) {
				UUID ownerUUID = entry.getKey();
				for (UUID authorizedUUID : entry.getValue()) {
					writer.write("global:" + ownerUUID.toString() + "=" + authorizedUUID.toString());
					writer.newLine();
				}
			}
			// 保存公开箱子数据
			for (String locationKey : publicChests) {
				writer.write("public:" + locationKey);
				writer.newLine();
			}
			// 保存漏斗传输状态
			for (String locationKey : hopperEnabledChests) {
				writer.write("hopper:" + locationKey);
				writer.newLine();
			}
			// 保存密码箱数据
			for (Map.Entry<String, String> entry : chestPasswords.entrySet()) {
				writer.write("password:" + entry.getKey() + "=" + entry.getValue());
				writer.newLine();
			}
			// 保存玩家默认公开/私有设置
			for (Map.Entry<UUID, Boolean> entry : playerDefaultPublicSettings.entrySet()) {
				writer.write("playerDefault:" + entry.getKey().toString() + "=" + entry.getValue());
				writer.newLine();
			}
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "无法保存箱子数据", e);
		}
	}
	
	// 加载配置文件
	private void loadChestData() {
		if (!dataFile.exists()) {
			return;
		}
		
		try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("=")) {
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String key = parts[0];
						String value = parts[1];
						
						if (key.startsWith("owner:")) {
							String locationKey = key.substring(6);
							UUID ownerUUID = UUID.fromString(value);
							chestOwners.put(locationKey, ownerUUID);
						} else if (key.startsWith("perm:")) {
							String locationKey = key.substring(5);
							UUID playerUUID = UUID.fromString(value);
							chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>()).add(playerUUID);
						} else if (key.startsWith("global:")) {
							// 加载全局授权数据
							String ownerUUIDStr = key.substring(7);
							UUID ownerUUID = UUID.fromString(ownerUUIDStr);
							UUID authorizedUUID = UUID.fromString(value);
							globalPermissions.computeIfAbsent(ownerUUID, k -> new HashSet<>()).add(authorizedUUID);
						} else if (key.startsWith("public:")) {
							// 加载公开箱子数据
							String locationKey = key.substring(7);
							publicChests.add(locationKey);
						} else if (key.startsWith("hopper:")) {
							// 加载漏斗传输状态
							String locationKey = key.substring(7);
							hopperEnabledChests.add(locationKey);
						} else if (key.startsWith("password:")) {
							// 加载密码箱数据
							String[] parts2 = key.substring(9).split("=", 2);
							if (parts2.length == 2) {
								chestPasswords.put(parts2[0], parts2[1]);
							}
						} else if (key.startsWith("playerDefault:")) {
							// 加载玩家默认公开/私有设置
							String[] parts2 = key.substring(14).split("=", 2);
							if (parts2.length == 2) {
								UUID playerUUID = UUID.fromString(parts2[0]);
								Boolean isDefaultPublic = Boolean.parseBoolean(parts2[1]);
								playerDefaultPublicSettings.put(playerUUID, isDefaultPublic);
							}
						}
					}
				}
			}
			getLogger().info("已加载 " + chestOwners.size() + " 个箱子的数据，" + globalPermissions.size() + " 个全局授权关系，" + publicChests.size() + " 个公开箱子，" + hopperEnabledChests.size() + " 个开启漏斗传输的箱子，" + chestPasswords.size() + " 个密码箱，" + playerDefaultPublicSettings.size() + " 个玩家默认设置");
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "无法加载箱子数据", e);
		}
	}
	
	// 处理 /xlr 和 /xlrdel 指令
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("§c该指令只能由玩家执行！");
			return true;
		}
		
		Player player = (Player) sender;
		
		if (args.length != 1) {
			if (label.equalsIgnoreCase("xlr")) {
				player.sendMessage("§c用法: /xlr <玩家名称>");
				player.sendMessage("§c将您所有的箱子权限授予指定玩家（包括新箱子）");
			} else if (label.equalsIgnoreCase("xlrdel")) {
				player.sendMessage("§c用法: /xlrdel <玩家名称>");
				player.sendMessage("§c取消指定玩家对您所有箱子的权限");
			}
			return true;
		}
		
		String targetName = args[0];
		OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
		
		if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
			player.sendMessage("§c未找到玩家: §6" + targetName);
			return true;
		}
		
		UUID targetUUID = targetPlayer.getUniqueId();
		if (targetUUID.equals(player.getUniqueId())) {
			player.sendMessage("§c不能对自己操作！");
			return true;
		}
		
		if (label.equalsIgnoreCase("xlr")) {
			// 添加全局授权
			Set<UUID> authorizedPlayers = globalPermissions.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
			
			if (authorizedPlayers.contains(targetUUID)) {
				player.sendMessage("§c玩家 §6" + targetPlayer.getName() + " §c已经被授权了！");
				return true;
			}
			
			authorizedPlayers.add(targetUUID);
			
			// 同时给当前所有箱子添加权限
			for (Map.Entry<String, UUID> entry : chestOwners.entrySet()) {
				if (entry.getValue().equals(player.getUniqueId())) {
					String locationKey = entry.getKey();
					Set<UUID> chestPerms = chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>());
					chestPerms.add(targetUUID);
				}
			}
			
			saveChestData();
			player.sendMessage("§a已成功将您所有箱子的权限授予玩家 §6" + targetPlayer.getName());
			
		} else if (label.equalsIgnoreCase("xlrdel")) {
			// 删除全局授权
			Set<UUID> authorizedPlayers = globalPermissions.get(player.getUniqueId());
			
			if (authorizedPlayers == null || !authorizedPlayers.contains(targetUUID)) {
				player.sendMessage("§c玩家 §6" + targetPlayer.getName() + " §c没有被全局授权！");
				return true;
			}
			
			authorizedPlayers.remove(targetUUID);
			if (authorizedPlayers.isEmpty()) {
				globalPermissions.remove(player.getUniqueId());
			}
			
			// 从所有箱子中移除权限 - 先收集要删除的 key，避免 ConcurrentModificationException
			List<String> keysToRemove = new ArrayList<>();
			for (Map.Entry<String, Set<UUID>> entry : chestPermissions.entrySet()) {
				String locationKey = entry.getKey();
				UUID ownerUUID = chestOwners.get(locationKey);
				
				// 只处理属于当前玩家的箱子
				if (ownerUUID != null && ownerUUID.equals(player.getUniqueId())) {
					Set<UUID> chestPerms = entry.getValue();
					chestPerms.remove(targetUUID);
					if (chestPerms.isEmpty()) {
						keysToRemove.add(locationKey);
					}
				}
			}
			// 批量删除空权限条目
			for (String key : keysToRemove) {
				chestPermissions.remove(key);
			}
			
			saveChestData();
			player.sendMessage("§a已取消玩家 §6" + targetPlayer.getName() + " §a对您所有箱子的权限");
		}
		
		return true;
	}
	
	// 检查玩家是否有权限打开箱子（包括全局授权 + 公开箱子）
	private boolean hasChestPermission(String locationKey, Player player) {
		UUID ownerUUID = chestOwners.get(locationKey);
		
		// 如果是箱子所有者，直接返回 true
		if (ownerUUID != null && ownerUUID.equals(player.getUniqueId())) {
			return true;
		}
		
		// 公开箱子：所有人可以打开
		if (publicChests.contains(locationKey)) {
			return true;
		}
		
		// 检查单独权限
		Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
		if (allowedPlayers != null && allowedPlayers.contains(player.getUniqueId())) {
			return true;
		}
		
		return false;
	}
}
