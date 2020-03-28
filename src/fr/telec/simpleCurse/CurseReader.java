package fr.telec.simpleCurse;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import fr.telec.utils.ConfigAccessor;

public class CurseReader {

	public static final String CURSES_FILENAME = "curses.yml";
	private JavaPlugin plugin = null;
	private ConfigAccessor curses = null;

	public CurseReader(JavaPlugin plugin) {
		this.plugin = plugin;
		curses = new ConfigAccessor(plugin, CURSES_FILENAME);

		plugin.saveDefaultConfig();
		curses.saveDefaultConfig();
		
		reload();
	}

	public void reload() {
		plugin.reloadConfig();
		curses.reloadConfig();
	}

	public FileConfiguration getConfig() {
		return plugin.getConfig();
	}

	public FileConfiguration getCurses() {
		return curses.getConfig();
	}
}
