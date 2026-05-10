package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class Shan extends JavaPlugin implements Listener {
	
	// 存储箱子位置与所有者的映射关系
	private Map<String, UUID> chestOwners = new HashMap<>();
	private File dataFile;
	
	// 临时存储正在打开 GUI 的玩家，防止触发破坏事件
	private Set<UUID> guiOpeningPlayers = new HashSet<>();
	// 临时存储玩家打开的箱子位置，用于关闭 GUI 时更新状态
	private Map<UUID, String> playerOpenedChests = new HashMap<>();
	
	// GUI 界面标题
	private static final String GUI_TITLE = "§e箱子管理";
	// GUI 行数（3行 = 27格）
	private static final int GUI_ROWS = 3;
	
	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		
		// 初始化数据文件
		dataFile = new File(getDataFolder(), "chests.yml");
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		
		// 加载已保存的箱子数据
		loadChestData();
		
		Bukkit.getConsoleSender().sendMessage("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
	
	@Override
	public void onDisable() {
		// 保存箱子数据
		saveChestData();
		Bukkit.getConsoleSender().sendMessage("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
	
	/**
	 * 监听箱子放置事件，自动记录箱子所有者
	 */
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		Block block = event.getBlock();
		Player player = event.getPlayer();
		
		// 检查放置的是否是箱子
		if (isChest(block.getType())) {
			String locationKey = getLocationKey(block);
			chestOwners.put(locationKey, player.getUniqueId());
			saveChestData();
		}
	}
	
	/**
	 * 监听玩家右键点击事件
	 * - 非所有者：阻止打开箱子
	 * - 所有者：正常打开箱子
	 */
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
		
		// 箱子没有主人，允许正常打开
		if (ownerUUID == null) {
			return;
		}
		
		// 非所有者阻止打开
		if (!ownerUUID.equals(player.getUniqueId())) {
			event.setCancelled(true);
			player.sendMessage("§c这个箱子已被锁定，您无法打开！");
		}
	}
	
	/**
	 * 监听玩家左键点击事件
	 * 所有者 Shift+左键 打开 GUI 管理界面
	 */
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
		
		// 箱子没有主人，不处理
		if (ownerUUID == null) {
			return;
		}
		
		// 所有者 Shift+左键 打开 GUI
		if (ownerUUID.equals(player.getUniqueId()) && player.isSneaking()) {
			event.setCancelled(true);
			openGui(player, block);
		}
	}
	
	/**
	 * 监听方块破坏事件，阻止非所有者破坏箱子
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		Player player = event.getPlayer();
		
		// 如果玩家正在打开 GUI，阻止破坏
		if (guiOpeningPlayers.contains(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}
		
		// 检查破坏的是否是箱子
		if (!isChest(block.getType())) {
			return;
		}
		
		String locationKey = getLocationKey(block);
		UUID ownerUUID = chestOwners.get(locationKey);
		
		// 如果箱子有主人且不是当前玩家，则阻止破坏
		if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
			event.setCancelled(true);
			player.sendMessage("§c这个箱子已被锁定，您无法破坏！");
		} else if (ownerUUID != null) {
			// 如果是所有者破坏箱子，清除数据
			chestOwners.remove(locationKey);
			saveChestData();
		}
	}
	
	/**
	 * 阻止漏斗对锁定的箱子进行物品传输
	 * 上锁的箱子默认禁止漏斗输入和输出
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryMoveItem(InventoryMoveItemEvent event) {
		// 检查源箱子是否被锁定
		if (isLockedChest(event.getSource())) {
			event.setCancelled(true);
			return;
		}
		
		// 检查目标箱子是否被锁定
		if (isLockedChest(event.getDestination())) {
			event.setCancelled(true);
		}
	}
	
	/**
	 * 检查容器是否为锁定的箱子
	 */
	private boolean isLockedChest(Inventory inventory) {
		if (inventory == null || inventory.getHolder() == null) {
			return false;
		}
		
		org.bukkit.block.Block block = null;
		try {
			// 尝试获取 Block 容器
			Object holder = inventory.getHolder();
			if (holder instanceof org.bukkit.block.BlockState) {
				block = ((org.bukkit.block.BlockState) holder).getBlock();
			} else if (holder instanceof org.bukkit.block.DoubleChest) {
				// 双箱情况，获取左侧箱子
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
		return chestOwners.containsKey(locationKey);
	}
	
	/**
	 * 阻止玩家在 GUI 中移动/取出物品
	 */
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getView() == null) {
			return;
		}
		
		String title = event.getView().getTitle();
		if (!title.equals(GUI_TITLE)) {
			return;
		}
		
		// 阻止在箱子管理 GUI 中操作物品
		event.setCancelled(true);
		
		// 处理按钮点击逻辑
		Player player = (Player) event.getWhoClicked();
		int slot = event.getRawSlot();
		String chestLocation = playerOpenedChests.get(player.getUniqueId());
		
		if (chestLocation == null) {
			return;
		}
		
		switch (slot) {
			case 10: // 单独权限
				player.sendMessage("§a§l§n单独权限");
				// 后续可扩展单独权限功能
				break;
			case 12: // 批量权限
				player.sendMessage("§e§l§n批量权限");
				// 后续可扩展批量权限功能
				break;
			case 14: // 锁定开关
				player.sendMessage("§c§l§n锁定开关");
				// 后续可扩展锁定开关功能
				break;
			case 16: // 漏斗开关
				player.sendMessage("§b§l§n漏斗开关");
				// 后续可扩展漏斗开关功能
				break;
		}
	}
	
	/**
	 * 监听 GUI 关闭事件
	 */
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (event.getView() == null) {
			return;
		}
		
		String title = event.getView().getTitle();
		if (!title.equals(GUI_TITLE)) {
			return;
		}
		
		Player player = (Player) event.getPlayer();
		playerOpenedChests.remove(player.getUniqueId());
	}
	
	/**
	 * 打开箱子管理 GUI
	 */
	private void openGui(Player player, Block chestBlock) {
		// 标记玩家正在打开 GUI，防止触发破坏事件
		guiOpeningPlayers.add(player.getUniqueId());
		
		// 记录玩家打开的箱子位置
		playerOpenedChests.put(player.getUniqueId(), getLocationKey(chestBlock));
		
		// 延迟一 tick 后移除标记，确保 GUI 已打开
		Bukkit.getScheduler().runTaskLater(this, () -> {
			guiOpeningPlayers.remove(player.getUniqueId());
		}, 1L);
		
		Inventory gui = Bukkit.createInventory(null, GUI_ROWS * 9, GUI_TITLE);
		
		// 黑色玻璃板填充
		ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
		for (int i = 0; i < 27; i++) {
			gui.setItem(i, blackGlass);
		}
		
		// 第10格：箱子所有者的头颅
		ItemStack ownerHead = createPlayerHead(chestBlock);
		gui.setItem(10, ownerHead);
		
		// 第12格：末影箱
		ItemStack enderChest = createItem(Material.ENDER_CHEST, "§e批量权限", null);
		gui.setItem(12, enderChest);
		
		// 第14格：箱子
		ItemStack chest = createItem(Material.CHEST, "§c锁定开关", null);
		gui.setItem(14, chest);
		
		// 第16格：漏斗
		ItemStack hopper = createItem(Material.HOPPER, "§b漏斗开关", null);
		gui.setItem(16, hopper);
		
		player.openInventory(gui);
	}
	
	/**
	 * 创建物品
	 */
	private ItemStack createItem(Material material, String name, String... lore) {
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
	private ItemStack createPlayerHead(Block chestBlock) {
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
	 * 判断是否为箱子类型
	 */
	private boolean isChest(Material material) {
		return material == Material.CHEST || 
		       material == Material.TRAPPED_CHEST;
	}
	
	/**
	 * 获取方块位置的唯一标识
	 */
	private String getLocationKey(Block block) {
		return block.getWorld().getName() + ":" + 
		       block.getX() + "," + 
		       block.getY() + "," + 
		       block.getZ();
	}
	
	/**
	 * 保存箱子数据到文件
	 */
	private void saveChestData() {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
			for (Map.Entry<String, UUID> entry : chestOwners.entrySet()) {
				writer.write(entry.getKey() + "=" + entry.getValue().toString());
				writer.newLine();
			}
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "无法保存箱子数据", e);
		}
	}
	
	/**
	 * 从文件加载箱子数据
	 */
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
						String locationKey = parts[0];
						UUID ownerUUID = UUID.fromString(parts[1]);
						chestOwners.put(locationKey, ownerUUID);
					}
				}
			}
			getLogger().info("已加载 " + chestOwners.size() + " 个箱子的数据");
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "无法加载箱子数据", e);
		}
	}
}
