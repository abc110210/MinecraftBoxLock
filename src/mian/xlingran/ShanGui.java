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
	
	// GUI名称
	private static final String BOX_MANAGE_TITLE = "§e箱子管理";
	private static final String SINGLE_PERMISSION_TITLE = "§a单独权限设置";
	private static final String PERMISSION_ADD_TITLE = "§a权限设置(单独)";
	private static final String PERMISSION_REMOVE_TITLE = "§c取消权限(单独)";
	// GUI行数
	private static final int GUI_ROWS = 4;      // 箱子管理 4行
	private static final int SINGLE_ROWS = 3;   // 单独权限设置 3行
	private static final int PERMISSION_ADD_ROWS = 6;    // 54格 (最大)
	private static final int PERMISSION_REMOVE_ROWS = 6; // 54格 (最大)
	
	// 打开箱子管理GUI界面 (4行)
	public static void openBoxManageGui(Player player, Block chestBlock, Map<String, UUID> chestOwners) {
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, BOX_MANAGE_TITLE);
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < 36; i++) {
			gui.setItem(i, blackGlass);
		}
		
		ItemStack ownerHead = createPlayerHead(chestBlock, chestOwners);
		gui.setItem(10, ownerHead);
		
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
	
	// 移除权限
	public static void openPermissionRemoveGui(Player player, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		Inventory gui = Bukkit.createInventory(null, PERMISSION_REMOVE_ROWS * 9, PERMISSION_REMOVE_TITLE);
		
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.getOrDefault(locationKey, new HashSet<>());
		
		List<OfflinePlayer> playersWithPermission = new ArrayList<>();
		for (UUID uuid : allowedPlayers) {
			playersWithPermission.add(Bukkit.getOfflinePlayer(uuid));
		}
		
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
		fillBorder(gui, PERMISSION_REMOVE_ROWS, blackGlass);
		
		List<Integer> innerSlots = getInnerSlots(PERMISSION_REMOVE_ROWS);
		int playerIndex = 0;
		
		for (int slot : innerSlots) {
			if (playerIndex < playersWithPermission.size()) {
				OfflinePlayer targetPlayer = playersWithPermission.get(playerIndex);
				ItemStack playerHead = createPermissionPlayerHead(targetPlayer, "§c");
				gui.setItem(slot, playerHead);
				playerIndex++;
			}
		}
		
		ItemStack returnButton = createItem(Material.WHITE_STAINED_GLASS_PANE, "§8返回");
		gui.setItem(53, returnButton);
		
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
	

	//
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
	
	// 箱子管理GUI点击
	public static void handleBoxManageClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		switch (slot) {
			case 10: 
				openSinglePermissionGui(player, chestBlock, chestOwners);
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
				openPermissionRemoveGui(player, chestBlock, chestOwners, chestPermissions);
				break;
			case 26: 
				openBoxManageGui(player, chestBlock, chestOwners);
				break;
		}
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
						player.sendMessage("§a已授予玩家 §6" + playerName + " §a打开此箱子的权限");
						player.closeInventory();
						return true;
					}
				}
			}
		}
		return false;
	}
	
	// 权限
	public static boolean handlePermissionRemoveClick(Player player, int slot, Block chestBlock, Map<String, UUID> chestOwners, Map<String, Set<UUID>> chestPermissions) {
		String locationKey = getLocationKey(chestBlock);
		Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
		
		if (allowedPlayers == null) {
			return false;
		}
		
		if (slot == 53) {
			openSinglePermissionGui(player, chestBlock, chestOwners);
			return false;
		}
		
		if (isInnerSlot(slot, PERMISSION_REMOVE_ROWS)) {
			Inventory gui = player.getOpenInventory().getTopInventory();
			ItemStack clickedItem = gui.getItem(slot);
			
			if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
				ItemMeta meta = clickedItem.getItemMeta();
				if (meta != null && meta.hasDisplayName()) {
					String playerName = meta.getDisplayName().replace("§c", "");
					
					OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
					if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
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
	
	// 判断内部区域
	private static boolean isInnerSlot(int slot, int rows) {
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
