package fr.telec.simpleCurse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;

import fr.telec.simpleCore.Language;

import org.apache.commons.lang.StringUtils;

//TODO Add async timer and event queue => better user experience
public class SimpleCurse extends JavaPlugin implements Listener {

	private Random r = new Random();
	private CurseReader cr;
	private Language lg;
	private static final String CURSE_TIME_KEY = "curse_time";

	/*
	 * Plugin setup
	 */

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		cr = new CurseReader(this);
		lg = new Language(this);
	}

	@Override
	public void onDisable() {
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("update")) {
			cr.reload();
			lg.reload();
			
			sender.sendMessage(ChatColor.GRAY + lg.get("updated"));
			return true;
		}
		else if (cmd.getName().equalsIgnoreCase("curses") || cmd.getName().equalsIgnoreCase("swears")) {
			sender.sendMessage(ChatColor.RED + lg.get("taboo"));
			for(String key : cr.getCurses().getKeys(false)) {
				sender.sendMessage(ChatColor.DARK_RED + key);
				for(String curse : cr.getCurses().getStringList(key)) {
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
	
			for (String key : cr.getCurses().getKeys(false)) {
				getLogger().log(Level.FINER, "key:" + key);
				List<String> curses = cr.getCurses().getStringList(key);
	
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
		evt.setCancelled(cr.getConfig().getBoolean("warn.cancel"));

		String msg = formatMessage(evt, bad_word, cr.getConfig().getStringList("warn.messages"));
		evt.getPlayer().sendMessage(msg);

		try {
			Sound sound = Sound.valueOf(""+cr.getConfig().getString("warn.sound"));
			evt.getPlayer().playSound(evt.getPlayer().getLocation(), sound, 10, 1);
		}catch (IllegalArgumentException  e) {}
	}

	public void doSlap(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(cr.getConfig().getBoolean("slap.cancel"));

		hit(evt.getPlayer(), cr.getConfig().getInt("slap.damages"));
		
		try {
			Sound sound = Sound.valueOf(""+cr.getConfig().getString("slap.sound"));
			evt.getPlayer().playSound(evt.getPlayer().getLocation(), sound, 10, 1);
		}catch (IllegalArgumentException  e) {}
	}

	public void doKick(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(cr.getConfig().getBoolean("kick.cancel"));
		
		//Check if not already kicked
		if(evt.getPlayer().isOnline()) {
			//Store in the player's metadata each time he curse
			int times = (int) getMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, 0) + 1;
			if(times >= cr.getConfig().getInt("kick.times")) { //And kick him when he reach the limit
				setMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, times);
				String msg = formatMessage(evt, bad_word, cr.getConfig().getStringList("kick.messages"));
				kick(evt.getPlayer(), msg);
			} else {
				setMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, 0);
			}
		}
	}

	public void doReplace(AsyncPlayerChatEvent evt, String bad_word) {
		evt.setCancelled(false);
		
		String good_word = formatMessage(evt, bad_word, cr.getConfig().getStringList("replace.by"));
		evt.setMessage(evt.getMessage().replace(bad_word, good_word));

		try {
			Sound sound = Sound.valueOf(""+cr.getConfig().getString("replace.sound"));
			evt.getPlayer().playSound(evt.getPlayer().getLocation(), sound, 10, 1);
		}catch (IllegalArgumentException  e) {}
	}

	/*
	 * Helpers
	 */

	private String formatMessage(AsyncPlayerChatEvent evt, String bad_word, List<String> messages) {
		String msg = messages.get(r.nextInt(messages.size()));

		msg = msg.replace("<player>", evt.getPlayer().getDisplayName())
		         .replace("<curse>", bad_word);

		return msg;
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
	
	private void setMetadata(JavaPlugin plugin, Metadatable object, String key, Object value) {
	  object.setMetadata(key, new FixedMetadataValue(plugin,value));
	}

	@SuppressWarnings("unused")
	private Object getMetadata(JavaPlugin plugin, Metadatable object, String key) {
		return getMetadata(plugin, object, key, null);
	}
	private Object getMetadata(JavaPlugin plugin, Metadatable object, String key, Object def) {
	  List<MetadataValue> values = object.getMetadata(key);  
	  for (MetadataValue value : values) {
	     // Plugins are singleton objects, so using == is safe here
	     if (value.getOwningPlugin() == plugin) {
	        return value.value();
	     }
	  }
	  return def;
	}
		
	private static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

}
