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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class Shan extends JavaPlugin implements Listener {
	
	// 存储箱子位置与所有者的映射关系
	private Map<String, UUID> chestOwners = new HashMap<>();
	private File dataFile;
	
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
	 * 监听玩家右键点击事件，阻止非所有者打开箱子
	 * 使用 HIGHEST 优先级确保在其他插件之后执行
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
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
		
		// 如果箱子有主人且不是当前玩家，则阻止打开
		if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
			event.setCancelled(true);
			player.sendMessage("§c这个箱子已被锁定，您无法打开！");
		}
	}
	
	/**
	 * 监听方块破坏事件，阻止非所有者破坏箱子
	 * 使用 HIGHEST 优先级确保在其他插件之后执行
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		Player player = event.getPlayer();
		
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
		try {
			StringBuilder content = new StringBuilder();
			for (Map.Entry<String, UUID> entry : chestOwners.entrySet()) {
				content.append(entry.getKey()).append("=").append(entry.getValue().toString()).append("\n");
			}
			org.apache.commons.io.FileUtils.writeStringToFile(dataFile, content.toString(), "UTF-8");
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
		
		try {
			java.util.List<String> lines = org.apache.commons.io.FileUtils.readLines(dataFile, "UTF-8");
			for (String line : lines) {
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
