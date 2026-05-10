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

import java.util.*;

public class ShanGui {
	
	// GUI 界面标题
	private static final String BOX_MANAGE_TITLE = "§e箱子管理";
	private static final String SINGLE_PERMISSION_TITLE = "§a单独权限设置";
	private static final String PERMISSION_ADD_TITLE = "§a权限设置(单独)";
	private static final String PERMISSION_REMOVE_TITLE = "§c取消权限(单独)";
	// GUI 行数
	private static final int GUI_ROWS = 3;
	private static final int PERMISSION_ADD_ROWS = 6;    // 54格 (最大)
	private static final int PERMISSION_REMOVE_ROWS = 6; // 54格 (最大)
	
	/**
	 * 打开箱子管理 GUI
	 */
	public static void openBoxManageGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, BOX_MANAGE_TITLE);
		
		// 黑色玻璃板填充
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 第10格：箱子所有者的头颅
		ItemStack ownerHead = createPlayerHead(chestBlock, chestOwners);
		gui.setItem(10, ownerHead);
		
		// 第12格：末影箱
		ItemStack enderChest = createItem(Material.ENDER_CHEST, "§e批量权限");
		gui.setItem(12, enderChest);
		
		// 第14格：箱子
		ItemStack chest = createItem(Material.CHEST, "§c锁定开关");
		gui.setItem(14, chest);
		
		// 第16格：漏斗
		ItemStack hopper = createItem(Material.HOPPER, "§b漏斗开关");
		gui.setItem(16, hopper);
		
		// 第26格：返回按钮
		ItemStack returnButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(25, returnButton);
		
		player.openInventory(gui);
	}
	
	/**
	 * 打开单独权限设置 GUI
	 */
	public static void openSinglePermissionGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, SINGLE_PERMISSION_TITLE);
		
		// 黑色玻璃板填充
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 第11格：命名牌 - 打开添加权限 GUI
		ItemStack nameTag = createItem(Material.NAME_TAG, "§a设置权限");
		gui.setItem(11, nameTag);
		
		// 第15格：信标 - 打开取消权限 GUI
		ItemStack beacon = createItem(Material.BEACON, "§c取消权限");
		gui.setItem(15, beacon);
		
		// 第26格：返回按钮
		ItemStack returnButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(25, returnButton);
		
		player.openInventory(gui);
	}
	
	/**
	 * 打开权限设置(单独) GUI - 添加权限
	 * 显示没有箱子打开权限的玩家头颅
	 */
	public static void openPermissionAddGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_ADD_ROWS * 9, PERMISSION_ADD_TITLE);
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.getOrDefault(locationKey, new HashSet<>());
		
		// 获取所有在线玩家
		Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
		List<Player> playersWithoutPermission = new ArrayList<>();
		
		for (Player onlinePlayer : onlinePlayers) {
			// 排除箱子所有者和已有权限的玩家
			UUID ownerUUID = chestOwners.get(locationKey);
			if (ownerUUID != null && onlinePlayer.getUniqueId().equals(ownerUUID)) {
				continue;
			}
			if (allowedPlayers.contains(onlinePlayer.getUniqueId())) {
				continue;
			}
			playersWithoutPermission.add(onlinePlayer);
		}
		
		// 填充黑色玻璃板边框
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorder(gui, PERMISSION_ADD_ROWS, blackGlass);
		
		// 内部区域填充玩家头颅（只在有玩家时填充，不填充空位）
		List<Integer> innerSlots = getInnerSlots(PERMISSION_ADD_ROWS);
		int playerIndex = 0;
		
		for (int slot : innerSlots) {
			if (playerIndex < playersWithoutPermission.size()) {
				Player targetPlayer = playersWithoutPermission.get(playerIndex);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§6");
				gui.setItem(slot, playerHead);
				playerIndex++;
			}
			// 没有玩家时不填充任何物品，保持空槽
		}
		
		// 第54格：返回按钮（索引53）
		ItemStack returnButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(53, returnButton);
		
		player.openInventory(gui);
	}
	
	/**
	 * 打开取消权限(单独) GUI - 移除权限
	 * 显示拥有箱子打开权限的玩家头颅
	 */
	public static void openPermissionRemoveGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_REMOVE_ROWS * 9, PERMISSION_REMOVE_TITLE);
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.getOrDefault(locationKey, new HashSet<>());
		
		// 获取有权限的玩家
		List<OfflinePlayer> playersWithPermission = new ArrayList<>();
		for (UUID uuid : allowedPlayers) {
			playersWithPermission.add(Bukkit.getOfflinePlayer(uuid));
		}
		
		// 填充黑色玻璃板边框
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorder(gui, PERMISSION_REMOVE_ROWS, blackGlass);
		
		// 内部区域填充玩家头颅（只在有玩家时填充，不填充空位）
		List<Integer> innerSlots = getInnerSlots(PERMISSION_REMOVE_ROWS);
		int playerIndex = 0;
		
		for (int slot : innerSlots) {
			if (playerIndex < playersWithPermission.size()) {
				OfflinePlayer targetPlayer = playersWithPermission.get(playerIndex);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§c");
				gui.setItem(slot, playerHead);
				playerIndex++;
			}
			// 没有玩家时不填充任何物品，保持空槽
		}
		
		// 第54格：返回按钮（索引53）
		ItemStack returnButton = createItem(Material.LIME_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(53, returnButton);
		
		player.openInventory(gui);
	}
	
	/**
	 * 填充 GUI 边框为黑色玻璃板
	 */
	private static void fillBorder(Inventory gui, int rows, ItemStack blackGlass) {
		int cols = 9;
		
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				// 第一行、最后一行、第一列、最后一列
				if (row == 0 || row == rows - 1 || col == 0 || col == cols - 1) {
					int slot = row * cols + col;
					gui.setItem(slot, blackGlass);
				}
			}
		}
	}
	
	/**
	 * 获取内部区域的槽位列表（排除边框）
	 */
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
	
	/**
	 * 判断是否为箱子管理 GUI
	 */
	public static boolean isBoxManageGui(String title) {
		return BOX_MANAGE_TITLE.equals(title);
	}
	
	/**
	 * 判断是否为单独权限设置 GUI
	 */
	public static boolean isSinglePermissionGui(String title) {
		return SINGLE_PERMISSION_TITLE.equals(title);
	}
	
	/**
	 * 判断是否为权限设置(单独) GUI
	 */
	public static boolean isPermissionAddGui(String title) {
		return PERMISSION_ADD_TITLE.equals(title);
	}
	
	/**
	 * 判断是否为取消权限(单独) GUI
	 */
	public static boolean isPermissionRemoveGui(String title) {
		return PERMISSION_REMOVE_TITLE.equals(title);
	}
	
	/**
	 * 处理箱子管理 GUI 的按钮点击
	 */
	public static void handleBoxManageClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners) {
		switch (slot) {
			case 10: // 单独权限 - 打开单独权限设置 GUI
				openSinglePermissionGui(player, chestBlock, chestOwners);
				break;
			case 12: // 批量权限
				player.sendMessage("§e§l§n批量权限");
				break;
			case 14: // 锁定开关
				player.sendMessage("§c§l§n锁定开关");
				break;
			case 16: // 漏斗开关
				player.sendMessage("§b§l§n漏斗开关");
				break;
			case 25: // 返回按钮 - 关闭GUI
				player.closeInventory();
				break;
		}
	}
	
	/**
	 * 处理单独权限设置 GUI 的按钮点击
	 */
	public static void handleSinglePermissionClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		switch (slot) {
			case 11: // 设置权限 - 打开添加权限 GUI
				openPermissionAddGui(player, chestBlock, chestOwners, chestPermissions);
				break;
			case 15: // 取消权限 - 打开取消权限 GUI
				openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions);
				break;
			case 25: // 返回按钮 - 返回箱子管理GUI
				openBoxManageGui(player, chestBlock, chestOwners);
				break;
		}
	}
	
	/**
	 * 处理权限设置(单独) GUI 的按钮点击 - 添加权限
	 */
	public static boolean handlePermissionAddClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>());
		
		// 检查是否点击返回按钮（第54格，索引53）
		if (slot == 53) {
			openSinglePermissionGui(player, chestBlock, chestOwners);
			return false;
		}
		
		// 检查点击的是否是内部区域（非边框）
		if (isInnerSlot(slot, PERMISSION_ADD_ROWS)) {
			// 获取点击的物品
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§6", "");
					
					// 查找对应的玩家 UUID
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
						// 授予权限
						allowedPlayers.add(targetPlayer.getUniqueId());
						player.sendMessage("§a已授予玩家 §6" + playerName + " §a打开此箱子的权限");
						player.closeInventory();
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * 处理取消权限(单独) GUI 的按钮点击 - 移除权限
	 */
	public static boolean handlePermissionRemoveClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
		
		if (allowedPlayers == null) {
			return false;
		}
		
		// 检查是否点击返回按钮（第54格，索引53）
		if (slot == 53) {
			openSinglePermissionGui(player, chestBlock, chestOwners);
			return false;
		}
		
		// 检查点击的是否是内部区域（非边框）
		if (isInnerSlot(slot, PERMISSION_REMOVE_ROWS)) {
			// 获取点击的物品
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§c", "");
					
					// 查找对应的玩家 UUID
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
						// 取消权限
						allowedPlayers.remove(targetPlayer.getUniqueId());
						player.sendMessage("§c已取消玩家 §6" + playerName + " §c打开此箱子的权限");
						player.closeInventory();
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * 判断槽位是否在内部区域（非边框）
	 */
	private static boolean isInnerSlot(int slot, int rows) {
		int row = slot / 9;
		int col = slot % 9;
		// 内部区域：排除第一行、最后一行、第一列、最后一列
		return row >= 1 && row < rows - 1 && col >= 1 && col <= 7;
	}
	
	/**
	 * 创建物品
	 */
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
	
	/**
	 * 创建玩家头颅（箱子所有者）
	 */
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
	
	/**
	 * 创建权限玩家头颅（在线玩家）
	 */
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
	
	/**
	 * 创建权限玩家头颅（离线玩家）
	 */
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
	
	/**
	 * 获取方块位置的唯一标识
	 */
	private static String getLocationKey(Block block) {
		return block.getWorld().getName() + ":" + 
		       block.getX() + "," + 
		       block.getY() + "," + 
		       block.getZ();
	}
}
