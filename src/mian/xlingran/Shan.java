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
import java.util.*;
import java.util.logging.Level;

public class Shan extends JavaPlugin implements Listener {
	
	// 存储箱子位置与所有者的映射关系
	private Map<String, UUID> chestOwners = new HashMap<>();
	private Map<String, Set<UUID>> chestPermissions = new HashMap<>();
	private File dataFile;
	
	private Set<UUID> guiOpeningPlayers = new HashSet<>();
	private Map<UUID, String> playerOpenedChests = new HashMap<>();
	private Set<UUID> switchingGuiPlayers = new HashSet<>();
	
	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		
		// 注册指令
		this.getCommand("xlr").setExecutor(this);
		
		// 初始化
		dataFile = new File(getDataFolder(), "chests.yml");
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		
		// 加载数据
		loadChestData();
		
		Bukkit.getConsoleSender().sendMessage("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
	
	@Override
	public void onDisable() {
		// 保存
		saveChestData();
		Bukkit.getConsoleSender().sendMessage("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
	
	//监听防止事件
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
		
		if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
			event.setCancelled(true);
			player.sendMessage("§c这个箱子已被锁定，您无法破坏！");
		} else if (ownerUUID != null) {
			chestOwners.remove(locationKey);
			chestPermissions.remove(locationKey);
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
		return chestOwners.containsKey(locationKey);
	}
	
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
		
		if (ShanGui.isBoxManageGui(title)) {
			event.setCancelled(true);
			if (slot == 10 || slot == 12) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				final int s = slot;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.handleBoxManageClick(player, s, cb, chestOwners, chestPermissions);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			} else {
				ShanGui.handleBoxManageClick(player, slot, chestBlock, chestOwners, chestPermissions);
			}
		}
		else if (ShanGui.isSinglePermissionGui(title)) {
			event.setCancelled(true);
			if (slot == 11 || slot == 15 || slot == 26) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				final int s = slot;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.handleSinglePermissionClick(player, s, cb, chestOwners, chestPermissions);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			}
		}
		else if (ShanGui.isPermissionAddGui(title)) {
			event.setCancelled(true);
			if (slot == 53) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.openSinglePermissionGui(player, cb, chestOwners);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
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
			if (slot == 53) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.openSinglePermissionGui(player, cb, chestOwners);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			} else {
				boolean closed = ShanGui.handlePermissionRemoveClick(player, slot, chestBlock, chestOwners, chestPermissions);
				if (closed) {
					saveChestData();
				}
			}
		}
		else if (ShanGui.isBatchPermissionGui(title)) {
			event.setCancelled(true);
			if (slot == 10 || slot == 14 || slot == 26) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				final int s = slot;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.handleBatchPermissionClick(player, s, cb, chestOwners, chestPermissions);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			}
		}
		else if (ShanGui.isBatchAddGui(title)) {
			event.setCancelled(true);
			if (slot == 53) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.openBatchPermissionGui(player, cb, chestOwners);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
			}
		}
		else if (ShanGui.isBatchRemoveGui(title)) {
			event.setCancelled(true);
			if (slot == 53) {
				switchingGuiPlayers.add(player.getUniqueId());
				final String loc = chestLocation;
				Bukkit.getScheduler().runTaskLater(this, () -> {
					Block cb = parseBlockLocation(player, loc);
					if (cb != null) {
						ShanGui.openBatchPermissionGui(player, cb, chestOwners);
					}
					switchingGuiPlayers.remove(player.getUniqueId());
				}, 2L);
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
			!ShanGui.isBatchPermissionGui(title) &&
			!ShanGui.isBatchAddGui(title) &&
			!ShanGui.isBatchRemoveGui(title)) {
			return;
		}
		
		Player player = (Player) event.getPlayer();
		
		if (switchingGuiPlayers.contains(player.getUniqueId())) {
			Bukkit.getScheduler().runTaskLater(this, () -> {
				switchingGuiPlayers.remove(player.getUniqueId());
			}, 1L);
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
		
		ShanGui.openBoxManageGui(player, chestBlock, chestOwners);
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
						}
					}
				}
			}
			getLogger().info("已加载 " + chestOwners.size() + " 个箱子的数据");
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "无法加载箱子数据", e);
		}
	}
	
	// 处理 /xlr 指令
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("§c该指令只能由玩家执行！");
			return true;
		}
		
		Player player = (Player) sender;
		
		if (args.length != 1) {
			player.sendMessage("§c用法: /xlr <玩家名称>");
			player.sendMessage("§c将您所有的箱子权限授予指定玩家");
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
			player.sendMessage("§c不能给自己授权！");
			return true;
		}
		
		// 统计授权的箱子数量
		int authorizedCount = 0;
		for (Map.Entry<String, UUID> entry : chestOwners.entrySet()) {
			if (entry.getValue().equals(player.getUniqueId())) {
				String locationKey = entry.getKey();
				Set<UUID> allowedPlayers = chestPermissions.computeIfAbsent(locationKey, k -> new HashSet<>());
				if (allowedPlayers.add(targetUUID)) {
					authorizedCount++;
				}
			}
		}
		
		saveChestData();
		
		player.sendMessage("§a已成功将 §6" + authorizedCount + " §a个箱子的权限授予玩家 §6" + targetPlayer.getName());
		
		// 如果目标玩家在线，通知他
		Player onlineTarget = Bukkit.getPlayer(targetUUID);
		if (onlineTarget != null && onlineTarget.isOnline()) {
			onlineTarget.sendMessage("§a玩家 §6" + player.getName() + " §a已将他的所有箱子权限授予给您！");
		}
		
		return true;
	}
	
	// 检查玩家是否有权限打开箱子（包括全局授权）
	private boolean hasChestPermission(String locationKey, Player player) {
		UUID ownerUUID = chestOwners.get(locationKey);
		
		// 如果是箱子所有者，直接返回 true
		if (ownerUUID != null && ownerUUID.equals(player.getUniqueId())) {
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
