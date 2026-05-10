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

import java.util.*;

public class ShanGui {
	
	private static Plugin plugin;
	
	public static void setPlugin(Plugin p) {
		plugin = p;
	}
	
	// GUI名称
	private static final String BOX_MANAGE_TITLE = "§e箱子管理";
	private static final String SINGLE_PERMISSION_TITLE = "§b单独权限设置";
	private static final String PERMISSION_ADD_TITLE = "§a权限设置(单独)";
	private static final String PERMISSION_REMOVE_TITLE = "§c取消权限(单独)";
	private static final String GLOBAL_PERMISSION_TITLE = "§a全局权限设置";
	private static final String GLOBAL_ADD_TITLE = "§a添加全局权限";
	private static final String GLOBAL_REMOVE_TITLE = "§c删除全局权限";
	// GUI行数
	private static final int GUI_ROWS = 3;      // 箱子管理 3行
	private static final int SINGLE_ROWS = 3;   // 单独权限设置 3行
	private static final int PERMISSION_ADD_ROWS = 6;    // 54格 (最大)
	private static final int PERMISSION_REMOVE_ROWS = 6; // 54格 (带分页)
	private static final int GLOBAL_ROWS = 3;   // 全局权限设置 3行
	// 分页常量
	private static final int PLAYERS_PER_PAGE = 28; // 每页显示玩家数量 (4行×7列)
	private static final int NEXT_PAGE_SLOT = 47;   // 下一页按钮 (黄绿色玻璃)
	private static final int PREV_PAGE_SLOT = 53;   // 上一页/返回按钮 (白玻璃, 第1页时返回上一级)
	
	// 打开箱子管理GUI界面 (3行)
	public static void openBoxManageGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, BOX_MANAGE_TITLE);
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack ownerHead = createPlayerHead(chestBlock, chestOwners);
		gui.setItem(10, ownerHead);
		
		ItemStack wheat = createItem(Material.WHEAT, "§b全局权限设置");
		gui.setItem(12, wheat);
		
		ItemStack chest = createItem(Material.CHEST, "§9锁定开关");
		gui.setItem(14, chest);
		
		ItemStack hopper = createItem(Material.HOPPER, "§b漏斗开关");
		gui.setItem(16, hopper);
		
		player.openInventory(gui);
	}
	
	// 打开单独权限设置GUI (3行)
	public static void openSinglePermissionGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, SINGLE_ROWS * 9, SINGLE_PERMISSION_TITLE);
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack nameTag = createItem(Material.NAME_TAG, "§a设置权限");
		gui.setItem(11, nameTag);
		
		ItemStack beacon = createItem(Material.BEACON, "§c取消权限");
		gui.setItem(15, beacon);
		
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(26, returnButton);
		
		player.openInventory(gui);
	}
	
	// 打开全局权限设置GUI (3行)
	public static void openGlobalPermissionGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, GLOBAL_ROWS * 9, GLOBAL_PERMISSION_TITLE);
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack boneMeal = createItem(Material.BONE_MEAL, "§a添加全局权限");
		gui.setItem(11, boneMeal);
		
		ItemStack feather = createItem(Material.FEATHER, "§c删除全局权限");
		gui.setItem(15, feather);
		
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(26, returnButton);
		
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
	
	// 打开删除全局权限 GUI (7行，带分页)
	public static void openGlobalRemoveGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions, int page) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_REMOVE_ROWS * 9, GLOBAL_REMOVE_TITLE);
		
		UUID ownerUUID = chestOwners.get(getLocationKey(chestBlock));
		Set<UUID> globallyAuthorized = globalPermissions.getOrDefault(ownerUUID, new HashSet<>());
		
		List<OfflinePlayer> playersWithGlobalPermission = new ArrayList<>();
		for (UUID uuid : globallyAuthorized) {
			playersWithGlobalPermission.add(Bukkit.getOfflinePlayer(uuid));
		}
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorder(gui, PERMISSION_REMOVE_ROWS, blackGlass);
		
		int totalPages = Math.max(1, (int) Math.ceil((double) playersWithGlobalPermission.size() / PLAYERS_PER_PAGE));
		if (page < 0) page = 0;
		if (page >= totalPages) page = totalPages - 1;
		
		// 计算当前页的玩家范围
		int startIndex = page * PLAYERS_PER_PAGE;
		int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, playersWithGlobalPermission.size());
		
		// 获取内部格子（第2-6行，第2-8列 = 35格）
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
			ItemStack nextBlank = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
			gui.setItem(NEXT_PAGE_SLOT, nextBlank);
		}
		
		// 上一页按钮
		if (page > 0) {
			ItemStack prevButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8上一页");
			gui.setItem(PREV_PAGE_SLOT, prevButton);
		} else {
			ItemStack prevBlank = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
			gui.setItem(PREV_PAGE_SLOT, prevBlank);
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
		fillBorder(gui, PERMISSION_REMOVE_ROWS, blackGlass);
		
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
			ItemStack nextBlank = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
			gui.setItem(NEXT_PAGE_SLOT, nextBlank);
		}
		
		// 上一页按钮
		if (page > 0) {
			ItemStack prevButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8上一页");
			gui.setItem(PREV_PAGE_SLOT, prevButton);
		} else {
			ItemStack prevBlank = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
			gui.setItem(PREV_PAGE_SLOT, prevBlank);
		}
		
		player.openInventory(gui);
	}
	
	// 玻璃板
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
		// 只取第2-5行的内部格子 (4行 × 7列 = 28格)
		for (int row = 1; row < rows - 1; row++) {
			for (int col = 1; col < cols - 1; col++) {
				slots.add(row * cols + col);
			}
		}
		return slots;
	}
	
	// 判断Gui
	public static boolean isBoxManageGui(String title) {
		return BOX_MANAGE_TITLE.equals(title);
	}
	
	public static boolean isSinglePermissionGui(String title) {
		return SINGLE_PERMISSION_TITLE.equals(title);
	}
	
	public static boolean isPermissionAddGui(String title) {
		return PERMISSION_ADD_TITLE.equals(title);
	}
	
	public static boolean isPermissionRemoveGui(String title) {
		return PERMISSION_REMOVE_TITLE.equals(title);
	}
	
	public static boolean isGlobalPermissionGui(String title) {
		return GLOBAL_PERMISSION_TITLE.equals(title);
	}
	
	public static boolean isGlobalAddGui(String title) {
		return GLOBAL_ADD_TITLE.equals(title);
	}
	
	public static boolean isGlobalRemoveGui(String title) {
		return GLOBAL_REMOVE_TITLE.equals(title);
	}
	
	// 箱子管理GUI点击
	public static void handleBoxManageClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions, Map<UUID, Set<UUID>> globalPermissions) {
		switch (slot) {
			case 10: 
				openSinglePermissionGui(player, chestBlock, chestOwners);
				break;
			case 12: 
				openGlobalPermissionGui(player, chestBlock, chestOwners);
				break;
			case 14: 
				player.sendMessage("§c§l§n锁定开关");
				break;
			case 16: 
				player.sendMessage("§b§l§n漏斗开关");
				break;
		}
	}
	
	// 单独权限设置GUI点击
	public static void handleSinglePermissionClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		switch (slot) {
			case 11: 
				openPermissionAddGui(player, chestBlock, chestOwners, chestPermissions);
				break;
			case 15: 
				openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, 0);
				break;
			case 26: 
				openBoxManageGui(player, chestBlock, chestOwners);
				break;
		}
	}
	
	// 全局权限设置GUI点击
	public static void handleGlobalPermissionClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions) {
		switch (slot) {
			case 11: 
				openGlobalAddGui(player, chestBlock, chestOwners, globalPermissions);
				break;
			case 15: 
				openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, 0);
				break;
			case 26: 
				openBoxManageGui(player, chestBlock, chestOwners);
				break;
		}
	}
	
	// 添加全局权限
	public static boolean handleGlobalAddClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<UUID, Set<UUID>> globalPermissions, Map<String, Set<UUID>> chestPermissions, Set<UUID> switchingGuiPlayers) {
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
		UUID ownerUUID = chestOwners.get(getLocationKey(chestBlock));
		if (ownerUUID == null) {
			return false;
		}
		
		Set<UUID> authorizedPlayers = globalPermissions.get(ownerUUID);
		if (authorizedPlayers == null) {
			return false;
		}
		
		// 返回按钮
		if (slot == RETURN_SLOT) {
			playerGuiPages.remove(player.getUniqueId());
			switchingGuiPlayers.add(player.getUniqueId());
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				openGlobalPermissionGui(player, chestBlock, chestOwners);
				switchingGuiPlayers.remove(player.getUniqueId());
			}, 2L);
			return false;
		}
		
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
				openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, newPage);
			}
			return false;
		}
		
		// 下一页
		if (slot == NEXT_PAGE_SLOT) {
			int totalPages = Math.max(1, (int) Math.ceil((double) authorizedPlayers.size() / PLAYERS_PER_PAGE));
			int newPage = Math.min(totalPages - 1, currentPage + 1);
			playerGuiPages.put(player.getUniqueId(), newPage);
			openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, newPage);
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
						
						// 刷新当前页面
						int totalPages = Math.max(1, (int) Math.ceil((double) authorizedPlayers.size() / PLAYERS_PER_PAGE));
						int page = Math.min(currentPage, totalPages - 1);
						playerGuiPages.put(player.getUniqueId(), page);
						openGlobalRemoveGui(player, chestBlock, chestOwners, globalPermissions, page);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// 添加权限
	public static boolean handlePermissionAddClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
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
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
		
		if (allowedPlayers == null) {
			return false;
		}
		
		// 返回按钮
		if (slot == RETURN_SLOT) {
			playerGuiPages.remove(player.getUniqueId());
			openSinglePermissionGui(player, chestBlock, chestOwners);
			return false;
		}
		
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
				int newPage = currentPage - 1;
				playerGuiPages.put(player.getUniqueId(), newPage);
				openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, newPage);
			}
			return false;
		}
		
		// 下一页
		if (slot == NEXT_PAGE_SLOT) {
			int totalPages = Math.max(1, (int) Math.ceil((double) allowedPlayers.size() / PLAYERS_PER_PAGE));
			int newPage = Math.min(totalPages - 1, currentPage + 1);
			playerGuiPages.put(player.getUniqueId(), newPage);
			openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, newPage);
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
						
						// 刷新当前页面
						int totalPages = Math.max(1, (int) Math.ceil((double) allowedPlayers.size() / PLAYERS_PER_PAGE));
						int page = Math.min(currentPage, totalPages - 1);
						playerGuiPages.put(player.getUniqueId(), page);
						openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions, page);
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
			meta.setDisplayName("§a单独权限");
			
			if (ownerUUID != null) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUUID);
				meta.setOwningPlayer(offlinePlayer);
			}
			
			head.setItemMeta(meta);
		}
		
		return head;
	}
	
	private static ItemStack createPermissionPlayerHead(Player player, String colorCode) {
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		
		if (meta != null) {
			meta.setDisplayName(colorCode + player.getName());
			meta.setOwningPlayer(player);
			head.setItemMeta(meta);
		}
		
		return head;
	}
	
	private static ItemStack createPermissionPlayerHead(OfflinePlayer player, String colorCode) {
		ItemStack head = new ItemStack(Material.PLAYER_HEAD);
		SkullMeta meta = (SkullMeta) head.getItemMeta();
		
		if (meta != null) {
			meta.setDisplayName(colorCode + player.getName());
			meta.setOwningPlayer(player);
			head.setItemMeta(meta);
		}
		
		return head;
	}
	
	// 位置
	private static String getLocationKey(Block block) {
		return block.getWorld().getName() + ":" + 
		       block.getX() + "," + 
		       block.getY() + "," + 
		       block.getZ();
	}
}
