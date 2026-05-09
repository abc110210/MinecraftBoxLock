package com.xlingran.auth;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


public class XlingranAuth extends JavaPlugin implements Listener {

    private static final String GUI_TITLE = "§a§lXlingran Auth - Creator: shan";
    private static final int GUI_ROWS = 3;
    private static final int GUI_SIZE = GUI_ROWS * 9; // 27 slots

    @Override
    public void onEnable() {
        getLogger().info("XlingranAuth has been enabled!");
        getLogger().info("Author: shan | Website: www.xlingran.com");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("XlingranAuth has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        openAuthGUI(player);
        return true;
    }

    public void openAuthGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // Fill the entire GUI with green glass first
        ItemStack greenGlass = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 5); // Green stained glass pane

        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, greenGlass);
        }

        // Set black glass border (outer ring)
        ItemStack blackGlass = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15); // Black stained glass pane

        // Top row (slots 0-8)
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, blackGlass);
        }

        // Bottom row (slots 18-26)
        for (int i = 18; i < 27; i++) {
            gui.setItem(i, blackGlass);
        }

        // Left and right columns (middle row)
        gui.setItem(9, blackGlass);   // Left column, second row
        gui.setItem(17, blackGlass);  // Right column, second row

        // Open the GUI for the player
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;

        String title = event.getInventory().getTitle();
        if (title != null && title.equals(GUI_TITLE)) {
            event.setCancelled(true);
        }
    }
}
