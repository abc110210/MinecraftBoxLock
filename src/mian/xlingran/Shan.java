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

import java.io.*;
import java.util.*;
import java.util.logging.Level;

public class Shan extends JavaPlugin implements Listener {
	
	// 存储箱子位置与所有者的映射关系
	private Map<String, UUID> chestOwners = new HashMap<>();
	// 存储箱子位置与有权限玩家的映射关系
	private Map<String, Set<UUID>> chestPermissions = new HashMap<>();
	private File dataFile;
	
	// 临时存储正在打开 GUI 的玩家，防止触发破坏事件
	private Set<UUID> guiOpeningPlayers = new HashSet<>();
	// 临时存储玩家打开的箱子位置
	private Map<UUID, String> playerOpenedChests = new HashMap<>();
	// 标记正在切换 GUI 的玩家（防止 InventoryCloseEvent 清除数据）
	private Set<UUID> switchingGuiPlayers = new HashSet<>();
	
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
		
		if (isChest(block.getType())) {
			String locationKey = getLocationKey(block);
			chestOwners.put(locationKey, player.getUniqueId());
			saveChestData();
		}
	}
	
	/**
	 * 监听玩家右键点击事件
	 * - 非所有者且无权限：阻止打开箱子
	 * - 所有者或有权限：正常打开箱子
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
		
		if (ownerUUID == null) {
			return;
		}
		
		if (!ownerUUID.equals(player.getUniqueId())) {
			Set<UUID> allowedPlayers = chestPermissions.get(locationKey);
			if (allowedPlayers == null || !allowedPlayers.contains(player.getUniqueId())) {
				event.setCancelled(true);
				player.sendMessage("§c这个箱子已被锁定，您无法打开！");
			}
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
		
		if (ownerUUID == null) {
			return;
		}
		
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
		
		if (guiOpeningPlayers.contains(player.getUniqueId())) {
			event.setCancelled(true);
			return;
		}
		
		if (!isChest(block.getType())) {
			return;
		}
		
		String locationKey = getLocationKey(block);
		UUID ownerUUID = chestOwners.get(locationKey);
		
		if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
			event.setCancelled(true);
			player.sendMessage("§c这个箱子已被锁定，您无法破坏！");
		} else if (ownerUUID != null) {
			chestOwners.remove(locationKey);
			chestPermissions.remove(locationKey);
			saveChestData();
		}
	}
	
	/**
	 * 阻止漏斗对锁定的箱子进行物品传输
	 */
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
	
	/**
	 * 检查容器是否为锁定的箱子
	 */
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
		
		// 箱子管理 GUI
		if (ShanGui.isBoxManageGui(title)) {
			event.setCancelled(true);
			if (slot == 10) {
				switchingGuiPlayers.add(player.getUniqueId());
				ShanGui.handleBoxManageClick(player, slot, chestBlock, chestOwners);
			} else {
				ShanGui.handleBoxManageClick(player, slot, chestBlock, chestOwners);
			}
		}
		// 单独权限设置 GUI
		else if (ShanGui.isSinglePermissionGui(title)) {
			event.setCancelled(true);
			if (slot == 11 || slot == 15) {
				switchingGuiPlayers.add(player.getUniqueId());
				ShanGui.handleSinglePermissionClick(player, slot, chestBlock, chestOwners, chestPermissions);
			}
		}
		// 权限设置(单独) GUI - 添加权限
		else if (ShanGui.isPermissionAddGui(title)) {
			event.setCancelled(true);
			boolean closed = ShanGui.handlePermissionAddClick(player, slot, chestBlock, chestOwners, chestPermissions);
			if (closed) {
				saveChestData();
			}
		}
		// 取消权限(单独) GUI - 移除权限
		else if (ShanGui.isPermissionRemoveGui(title)) {
			event.setCancelled(true);
			boolean closed = ShanGui.handlePermissionRemoveClick(player, slot, chestBlock, chestOwners, chestPermissions);
			if (closed) {
				saveChestData();
			}
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
		if (!ShanGui.isBoxManageGui(title) && 
			!ShanGui.isSinglePermissionGui(title) && 
			!ShanGui.isPermissionAddGui(title) && 
			!ShanGui.isPermissionRemoveGui(title)) {
			return;
		}
		
		Player player = (Player) event.getPlayer();
		
		// 如果正在切换 GUI，不清除数据（1 tick 后新 GUI 会打开）
		if (switchingGuiPlayers.contains(player.getUniqueId())) {
			// 延迟 1 tick 清除标记，等待新 GUI 打开完成
			Bukkit.getScheduler().runTaskLater(this, () -> {
				switchingGuiPlayers.remove(player.getUniqueId());
			}, 1L);
			return;
		}
		
		playerOpenedChests.remove(player.getUniqueId());
	}
	
	/**
	 * 打开箱子管理 GUI
	 */
	private void openGui(Player player, Block chestBlock) {
		guiOpeningPlayers.add(player.getUniqueId());
		playerOpenedChests.put(player.getUniqueId(), getLocationKey(chestBlock));
		
		Bukkit.getScheduler().runTaskLater(this, () -> {
			guiOpeningPlayers.remove(player.getUniqueId());
		}, 1L);
		
		ShanGui.openBoxManageGui(player, chestBlock, chestOwners);
	}
	
	/**
	 * 从位置字符串解析 Block 对象
	 */
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
						}
					}
				}
			}
			getLogger().info("已加载 " + chestOwners.size() + " 个箱子的数据");
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "无法加载箱子数据", e);
		}
	}
}
