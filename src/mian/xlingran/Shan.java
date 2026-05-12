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
import org.bukkit.event.entity.EntityExplodeEvent;
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
	
	
	static {
		try {
			System.setProperty("file.encoding", "UTF-8");
			System.setProperty("sun.jnu.encoding", "UTF-8");
			
			
			java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
			for (Handler handler : rootLogger.getHandlers()) {
				if (handler instanceof ConsoleHandler) {
					handler.setEncoding("UTF-8");
				}
			}
		} catch (Exception e) {
			
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
	private Map<UUID, Boolean> playerDefaultHopperSettings = new HashMap<>(); // 玩家默认新箱子漏斗传输设置：true=打开, false/null=关闭
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
		
		// 指令
		this.getCommand("xlr").setExecutor(this);
		this.getCommand("xlrdel").setExecutor(this);
		
		// 初始化
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		
		saveResource("Gui.yml", false);
		saveResource("Message.yml", false);
		
		// 加载GUI配置
		ShanGui.loadGuiConfig(this);
		// 加载消息配置（必须在 saveResource 之后）
		MessageUtil.init(this);
		
		// 初始化 chests.yml
		dataFile = new File(getDataFolder(), "chests.yml");
		
		// 加载数据
		loadChestData();
		
		getLogger().info("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
	
	@Override
	public void onDisable() {
		// 保存
		saveChestData();
		getLogger().info("§c箱子锁插件已卸载，交流群: 943446220");
	}
	
	private void logInfo(String message) {
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
	
	//防止事件
	@EventHandler
	public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
	}

	// 聊天输入密码
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
			MessageUtil.sendMessage(player, "PasswordBoxSetFailed");
			return;
		}

		// 验证密码格式：4~8 位，仅英文和数字
		if (!message.matches("[a-zA-Z0-9]{4,8}")) {
			MessageUtil.sendMessage(player, "PasswordBoxLengthErro");
			return;
		}

		// 设置成功
		chestPasswords.put(chestLocation, message);
		saveChestData();
		waitingForPasswordSet.remove(player.getUniqueId());
		playerPendingPasswordChests.remove(player.getUniqueId());
		MessageUtil.sendMessage(player, "PasswordBoxSetSaved");

		Bukkit.getScheduler().runTask(this, () -> {
			Block chestBlock = parseBlockLocation(player, chestLocation);
			if (chestBlock != null) {
				ShanGui.openBoxManageGui(player, chestBlock, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
			}
		});
	}

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
			MessageUtil.sendMessage(player, "PasswordBoxCancelOpen");
			return;
		}

		// 验证密码
		String correctPassword = chestPasswords.get(chestLocation);
		if (correctPassword != null && message.equals(correctPassword)) {
			// 密码正确，给予临时打开权限（存储箱子位置和验证时的密码）
			verifiedPasswordChests.put(player.getUniqueId(), new AbstractMap.SimpleEntry<>(chestLocation, correctPassword));
			waitingForPasswordOpen.remove(player.getUniqueId());
			playerPendingPasswordChests.remove(player.getUniqueId());
			MessageUtil.sendMessage(player, "PasswordBoxCorrect");
		} else {
			MessageUtil.sendMessage(player, "PasswordBoxErro");
		}
	}

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
			
			Set<UUID> globallyAuthorized = globalPermissions.get(ownerUUID);
			if (globallyAuthorized != null && !globallyAuthorized.isEmpty()) {
				Set<UUID> chestPerms = chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>());
				chestPerms.addAll(globallyAuthorized);
			}
			
			Boolean defaultPublic = playerDefaultPublicSettings.get(ownerUUID);
			if (defaultPublic != null && defaultPublic) {
				publicChests.add(locationKey);
				MessageUtil.sendMessage(player, "PlacePubBox");
			} else {
				MessageUtil.sendMessage(player, "PlacePriBox");
			}
			
			// 应用玩家的默认漏斗传输设置
			Boolean defaultHopper = playerDefaultHopperSettings.get(ownerUUID);
			if (defaultHopper != null && defaultHopper) {
				// 如果玩家设置为默认打开漏斗，则新箱子自动开启漏斗传输
				hopperEnabledChests.add(locationKey);
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
		
		// 箱子主人直接允许打开
		if (ownerUUID.equals(player.getUniqueId())) {
			return;
		}
		
		// 密码箱优先如果箱子设置了密码
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
							
			if (isPasswordModified) {
				MessageUtil.sendMessage(player, "PasswordBoxChange");
			} else {
				MessageUtil.sendMessage(player, "PasswordBoxOpen");
			}
					
			waitingForPasswordOpen.add(player.getUniqueId());
			playerPendingPasswordChests.put(player.getUniqueId(), locationKey);
			return;
		}
		
		if (!hasChestPermission(locationKey, player)) {
			event.setCancelled(true);
			MessageUtil.sendMessage(player, "LockBox");
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
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityExplode(EntityExplodeEvent event) {
		if (event.isCancelled()) {
			return;
		}
		
		event.blockList().removeIf(block -> {
			if (!isChest(block.getType())) {
				return false;
			}
			
			String locationKey = getLocationKey(block);
			UUID ownerUUID = chestOwners.get(locationKey);
			
			if (ownerUUID != null) {
				return true; 
			}
			
			return false;
		});
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
				MessageUtil.sendMessage(player, "PrivateBreak");
				return;
			}
			
			// 私有箱子：无权限的玩家无法破坏
			if (!hasChestPermission(locationKey, player)) {
				event.setCancelled(true);
				MessageUtil.sendMessage(player, "LockBreak");
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
					ShanGui.handleBoxManageClick(player, s, cb, chestOwners, chestPermissions, globalPermissions, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings, playerDefaultHopperSettings);
				}
			}, 2L);
		}
		else if (ShanGui.isManagementPanelGui(title)) {
			event.setCancelled(true);
			// 处理返回按钮（第8格）
			if (slot == 8) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.openBoxManageGui(player, cb, chestOwners, publicChests, hopperEnabledChests, chestPasswords, playerDefaultPublicSettings);
					}
				}, 2L);
				return;
			}
			
			switchingGuiPlayers.add(player.getUniqueId());
			final String loc = chestLocation;
			final int s = slot;
			Bukkit.getScheduler().runTaskLater(this, () -> {
				Block cb = parseBlockLocation(player, loc);
				if (cb != null) {
					ShanGui.handleManagementPanelClick(player, s, cb, chestOwners, playerDefaultPublicSettings, playerDefaultHopperSettings);
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
	
	// 容器类型（支持箱子、熔炉、发射器等多种容器）
	private boolean isChest(Material material) {
		return material == Material.CHEST || 
		       material == Material.TRAPPED_CHEST ||
		       material == Material.FURNACE ||           // 熔炉
		       material == Material.BLAST_FURNACE ||     // 高炉
		       material == Material.SMOKER ||            // 烟熏炉
		       material == Material.DISPENSER ||         // 发射器
		       material == Material.DROPPER ||           // 投掷器
		       material == Material.CRAFTING_TABLE ||    // 合成台
		       material == Material.LOOM ||              // 织布机
		       material == Material.CRAFTER;             // 合成器
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
			// 保存玩家默认漏斗传输设置
			for (Map.Entry<UUID, Boolean> entry : playerDefaultHopperSettings.entrySet()) {
				writer.write("playerDefaultHopper:" + entry.getKey().toString() + "=" + entry.getValue());
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
						} else if (key.startsWith("playerDefaultHopper:")) {
							// 加载玩家默认漏斗传输设置
							String[] parts2 = key.substring(20).split("=", 2);
							if (parts2.length == 2) {
								UUID playerUUID = UUID.fromString(parts2[0]);
								Boolean isDefaultHopper = Boolean.parseBoolean(parts2[1]);
								playerDefaultHopperSettings.put(playerUUID, isDefaultHopper);
							}
						}
					}
				}
			}
			getLogger().info("已加载 " + chestOwners.size() + " 个箱子的数据，" + globalPermissions.size() + " 个全局授权关系，" + publicChests.size() + " 个公开箱子，" + hopperEnabledChests.size() + " 个开启漏斗传输的箱子，" + chestPasswords.size() + " 个密码箱，" + playerDefaultPublicSettings.size() + " 个玩家默认设置，" + playerDefaultHopperSettings.size() + " 个玩家默认漏斗设置");
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
		
		// 处理 reload 指令
		if (label.equalsIgnoreCase("xlr") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			// 检查权限（OP 或有权限节点）
			if (!player.hasPermission("xlr.reload") && !player.isOp()) {
				MessageUtil.sendMessage(player, "CommandNoPermission");
				return true;
			}
			
			// 保存当前数据
			saveChestData();
			
			// 关闭所有玩家打开的自定义GUI，防止重载后GUI失效
			// 通过检查标题是否包含特定关键词来识别自定义GUI
			for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
				String invTitle = onlinePlayer.getOpenInventory().getTitle();
				if (invTitle != null && (invTitle.contains("管理") || invTitle.contains("权限") || 
					invTitle.contains("锁定") || invTitle.contains("漏斗") || invTitle.contains("密码"))) {
					onlinePlayer.closeInventory();
				}
			}
			
			// 清空所有内存数据
			chestOwners.clear();
			chestPermissions.clear();
			globalPermissions.clear();
			publicChests.clear();
			hopperEnabledChests.clear();
			chestPasswords.clear();
			playerDefaultPublicSettings.clear();
			playerDefaultHopperSettings.clear();
			
			// 重新加载GUI配置
			ShanGui.loadGuiConfig(this);
			// 重新加载消息配置
			MessageUtil.reloadMessages();
			
			// 重新加载数据
			loadChestData();
			
			MessageUtil.sendMessage(player, "CommandReloadSuccess");
			return true;
		}
		
		if (args.length != 1) {
			if (label.equalsIgnoreCase("xlr")) {
				MessageUtil.sendMessage(player, "CommandUsageXlr");
				MessageUtil.sendMessage(player, "CommandUsageXlrDesc");
			} else if (label.equalsIgnoreCase("xlrdel")) {
				MessageUtil.sendMessage(player, "CommandUsageXlrdel");
				MessageUtil.sendMessage(player, "CommandUsageXlrdelDesc");
			} else if (label.equalsIgnoreCase("xlr") && args.length > 0) {
				// 如果参数存在但不是 reload（前面已处理）
				MessageUtil.sendMessage(player, "CommandUsageXlr");
			}
			return true;
		}
		
		String targetName = args[0];
		OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
		
		if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
			Map<String, String> vars = Map.of("player", targetName);
			MessageUtil.sendMessage(player, "CommandPlayerNotFound", vars);
			return true;
		}
		
		UUID targetUUID = targetPlayer.getUniqueId();
		if (targetUUID.equals(player.getUniqueId())) {
			MessageUtil.sendMessage(player, "CommandSelfTarget");
			return true;
		}
		
		if (label.equalsIgnoreCase("xlr")) {
			// 添加全局授权
			Set<UUID> authorizedPlayers = globalPermissions.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
			
			if (authorizedPlayers.contains(targetUUID)) {
				Map<String, String> vars = Map.of("player", targetPlayer.getName());
				MessageUtil.sendMessage(player, "CommandAlreadyAuthorized", vars);
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
			Map<String, String> vars = Map.of("player", targetPlayer.getName());
			MessageUtil.sendMessage(player, "CommandGlobalAddSuccess", vars);
			
		} else if (label.equalsIgnoreCase("xlrdel")) {
			// 删除全局授权
			Set<UUID> authorizedPlayers = globalPermissions.get(player.getUniqueId());
			
			if (authorizedPlayers == null || !authorizedPlayers.contains(targetUUID)) {
				Map<String, String> vars = Map.of("player", targetPlayer.getName());
				MessageUtil.sendMessage(player, "CommandNotGlobalAuthorized", vars);
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
			Map<String, String> vars = Map.of("player", targetPlayer.getName());
			MessageUtil.sendMessage(player, "CommandGlobalRemoveSuccess", vars);
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
		
		// 检查全局权限
		if (ownerUUID != null) {
			Set<UUID> globallyAuthorized = globalPermissions.get(ownerUUID);
			if (globallyAuthorized != null && globallyAuthorized.contains(player.getUniqueId())) {
				return true;
			}
		}
		
		// 检查单独权限
		Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
		if (allowedPlayers != null && allowedPlayers.contains(player.getUniqueId())) {
			return true;
		}
		
		return false;
	}
}
