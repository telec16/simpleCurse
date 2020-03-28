package fr.telec.simpleCurse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.telec.simpleCore.ConfigAccessor;
import fr.telec.simpleCore.Language;
import fr.telec.simpleCore.MetadataAccessor;
import fr.telec.simpleCore.StringHandler;

import org.apache.commons.lang.StringUtils;

//TODO Add async timer and event queue => better user experience
public class SimpleCurse extends JavaPlugin implements Listener {

	private static final String CURSE_TIME_KEY = "curse_time";
	private static final String CURSES_FILENAME = "curses.yml";
	
	private Random r = new Random();
	private ConfigAccessor cr;
	private Language lg;

	/*
	 * Plugin setup
	 */

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		
		saveDefaultConfig();
		reloadConfig();
		
		cr = new ConfigAccessor(this, CURSES_FILENAME);
		cr.saveDefaultConfig();
		cr.reloadConfig();
		
		lg = new Language(this);
	}

	@Override
	public void onDisable() {
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("update")) {
			reloadConfig();
			cr.reloadConfig();
			lg.reload();

			sender.sendMessage(ChatColor.GRAY + "[" + getName() + "]" + lg.get("updated"));
			return true;
		}
		else if (cmd.getName().equalsIgnoreCase("curses")) {
			sender.sendMessage(StringHandler.colorize(ChatColor.RED + lg.get("taboo")));
			for(String key : getConfig().getKeys(false)) {
				sender.sendMessage(ChatColor.DARK_RED + key);
				for(String curse : cr.getConfig().getStringList(key)) {
					sender.sendMessage(" - " + curse);
				}
			}
			return true;
		}
		return false;
	}

	/*
	 * On every messages:
	 * 1. Go through the keys in curses.yml
	 * 2. If at least one curse is detected
	 * 3. Check for every curses at every positions of the message
	 * 4. Call each time the appropriate method
	 */

	@EventHandler
	public void onAsyncPlayerChatEvent(AsyncPlayerChatEvent evt) {
		if(evt.getPlayer() != null && evt.getPlayer().isOnline()) {
			String msg = evt.getMessage().toLowerCase();
	
			for (String key : cr.getConfig().getKeys(false)) {
				getLogger().log(Level.FINER, "key:" + key);
				List<String> curses = cr.getConfig().getStringList(key);
	
				// First check if there is a bad word
				if (StringUtils.indexOfAny(msg, curses.toArray(new String[0])) != -1) {
					getLogger().log(Level.FINER, "|-bad");
					// Retrieve the corresponding method
					Method method = null;
					try {
						method = this.getClass().getMethod("do" + capitalize(key), 
														   AsyncPlayerChatEvent.class, String.class);
					}
					catch (SecurityException e) { getLogger().log(Level.SEVERE, "SecurityException", e); }
					catch (NoSuchMethodException e) { getLogger().log(Level.SEVERE, "NoSuchMethodException", e); }
	
					if (method != null) {
						for (String curse : curses) {
							getLogger().log(Level.FINER, "| |-" + curse);
							for (int i = 0; i < msg.length(); i++) {
								if (msg.startsWith(curse, i)) { // We have a cursed word!
									getLogger().log(Level.FINER, "| | |-at " + i);
									try {
										method.invoke(this, evt, curse);
									}
									catch (IllegalArgumentException e) { getLogger().log(Level.SEVERE, "IllegalArgumentException", e); }
									catch (IllegalAccessException e) { getLogger().log(Level.SEVERE, "IllegalAccessException", e); }
									catch (InvocationTargetException e) { getLogger().log(Level.SEVERE, "InvocationTargetException", e); }
								}
							}
						}
					}
				}
			}
		}
	}

	/*
	 * Actions
	 */

	public void doWarn(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(getConfig().getBoolean("warn.cancel"));

		String msg = formatMessage(evt.getPlayer(), bad_word, getConfig().getStringList("warn.messages"));
		evt.getPlayer().sendMessage(msg);

		try {
			Sound sound = Sound.valueOf(""+getConfig().getString("warn.sound"));
			evt.getPlayer().playSound(evt.getPlayer().getLocation(), sound, 10, 1);
		}catch (IllegalArgumentException  e) {}
	}

	public void doSlap(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(getConfig().getBoolean("slap.cancel"));

		hit(evt.getPlayer(), getConfig().getInt("slap.damages"));
		
		try {
			Sound sound = Sound.valueOf(""+getConfig().getString("slap.sound"));
			evt.getPlayer().playSound(evt.getPlayer().getLocation(), sound, 10, 1);
		}catch (IllegalArgumentException  e) {}
	}

	public void doKick(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(getConfig().getBoolean("kick.cancel"));
		
		//Check if not already kicked
		if(evt.getPlayer().isOnline()) {
			//Store in the player's metadata each time he curse
			int times = (int) MetadataAccessor.getMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, 0) + 1;
			if(times >= getConfig().getInt("kick.times")) { //And kick him when he reach the limit
				MetadataAccessor.setMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, times);
				String msg = formatMessage(evt.getPlayer(), bad_word, getConfig().getStringList("kick.messages"));
				kick(evt.getPlayer(), msg);
			} else {
				MetadataAccessor.setMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, 0);
			}
		}
	}

	public void doReplace(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(false);
		
		String good_word = formatMessage(evt.getPlayer(), bad_word, getConfig().getStringList("replace.by"));
		evt.setMessage(evt.getMessage().replace(bad_word, good_word));

		try {
			Sound sound = Sound.valueOf(""+getConfig().getString("replace.sound"));
			evt.getPlayer().playSound(evt.getPlayer().getLocation(), sound, 10, 1);
		}catch (IllegalArgumentException  e) {}
	}

	/*
	 * Helpers
	 */

	private String formatMessage(Player player, String bad_word, List<String> messages) {
		String msg = messages.get(r.nextInt(messages.size()));

		Map<String, String> values = new HashMap<String, String>();
		values.put("player", player.getDisplayName());
		values.put("curse", bad_word);

		return StringHandler.translate(msg, values);
	}

	private void kick(Player player, String reason) {
		Bukkit.getScheduler().runTask(this, new Runnable() {
			public void run() {
				player.kickPlayer(reason);
			}
		});
	}

	private void hit(Player player, int damages) {
		Bukkit.getScheduler().runTask(this, new Runnable() {
			public void run() {
				int tck = player.getNoDamageTicks();
				player.setNoDamageTicks(0);
				player.damage(damages);
				player.setNoDamageTicks(tck);
			}
		});
	}
		
	private static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

}
