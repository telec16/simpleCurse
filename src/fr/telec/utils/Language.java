package fr.telec.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Language {

	public static final String LANGUAGE_FILENAME = "language.yml";
	private ConfigAccessor dictionaryAcs = null;
	private FileConfiguration dictionary = null;
	private String language = null; 

	public Language(JavaPlugin plugin) {
		dictionaryAcs = new ConfigAccessor(plugin, LANGUAGE_FILENAME);

		dictionaryAcs.saveDefaultConfig();
		
		reload();
	}

	public void reload() {
		dictionaryAcs.reloadConfig();
		dictionary = dictionaryAcs.getConfig();
		language = dictionary.getString("language");
	}
	
	public String get(String key) {
		return dictionary.getString("lang" + "." + language + "." + key);
		
	}
	
	public String getLanguage() {
		return language;
	}
}
