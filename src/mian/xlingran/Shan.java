package mian.xlingran;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Shan extends JavaPlugin {
	
	@Override
	public void onEnable() {
		Bukkit.getConsoleSender().sendMessage("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
	
	@Override
	public void onDisable() {
		Bukkit.getConsoleSender().sendMessage("§a欢迎使用 §b箱子锁 §a插件,交流群: 943446220");
	}
}
