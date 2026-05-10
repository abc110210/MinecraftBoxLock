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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShanGui {
	
	// GUI 界面标题
	private static final String BOX_MANAGE_TITLE = "§e箱子管理";
	private static final String SINGLE_PERMISSION_TITLE = "§a单独权限设置";
	// GUI 行数（3行 = 27格）
	private static final int GUI_ROWS = 3;
	
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
		
		// 第11格：命名牌
		ItemStack nameTag = createItem(Material.NAME_TAG, "§a设置权限");
		gui.setItem(11, nameTag);
		
		// 第15格：信标
		ItemStack beacon = createItem(Material.BEACON, "§c取消权限");
		gui.setItem(15, beacon);
		
		player.openInventory(gui);
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
		}
	}
	
	/**
	 * 处理单独权限设置 GUI 的按钮点击
	 */
	public static void handleSinglePermissionClick(Player player, int slot) {
		switch (slot) {
			case 11: // 设置权限
				player.sendMessage("§a§l§n设置权限");
				break;
			case 15: // 取消权限
				player.sendMessage("§c§l§n取消权限");
				break;
		}
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
				java.util.List<String> loreList = new java.util.ArrayList<>();
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
	 * 创建玩家头颅
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
	 * 获取方块位置的唯一标识
	 */
	private static String getLocationKey(Block block) {
		return block.getWorld().getName() + ":" + 
		       block.getX() + "," + 
		       block.getY() + "," + 
		       block.getZ();
	}
}
