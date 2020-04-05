package fr.telec.simpleCurse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
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
import fr.telec.simpleCore.SoundHelper;
import fr.telec.simpleCore.StringHandler;

public class SimpleCurse extends JavaPlugin implements Listener {

	private static final String CURSE_TIME_KEY = "curse_time";
	private static final String CURSES_FILENAME = "curses.yml";
	
	private Random r = new Random();
	
	private ConfigAccessor cr;
	private Language lg;
	private SoundHelper sh;

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
		
		sh = new SoundHelper(this);
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

			sender.sendMessage(ChatColor.GRAY + "[" + getName() + "] " + lg.get("updated"));
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
			ActionQueue aq = new ActionQueue(this, sh, evt);
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
						method = this.getClass().getMethod("do" + capitalize(key), AsyncPlayerChatEvent.class, String.class, ActionQueue.class);
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
										method.invoke(this, evt, curse, aq);
										aq.addAction(new ActionQueue.Delay(getConfig().getInt(key+".delay")));
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
			
			aq.executeActions();
		}
	}

	/*
	 * Actions
	 */

	public void doWarn(AsyncPlayerChatEvent evt, String curse, ActionQueue aq) {
		evt.setCancelled(getConfig().getBoolean("warn.cancel"));

		String msg = formatMessage(evt.getPlayer(), curse, getConfig().getStringList("warn.messages"));
		aq.addAction(new ActionQueue.Message(msg));

		aq.addAction(new ActionQueue.Sound("warn.sound"));
	}

	public void doSlap(AsyncPlayerChatEvent evt, String curse, ActionQueue aq) {
		evt.setCancelled(getConfig().getBoolean("slap.cancel"));

		aq.addAction(new ActionQueue.Hit(getConfig().getInt("slap.damages")));

		aq.addAction(new ActionQueue.Sound("slap.sound"));
	}

	public void doKick(AsyncPlayerChatEvent evt, String curse, ActionQueue aq) {
		evt.setCancelled(getConfig().getBoolean("kick.cancel"));
		
		//Check if not already kicked
		if(evt.getPlayer().isOnline()) {
			//Store in the player's metadata each time he curse
			int times = (int) MetadataAccessor.getMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, 0) + 1;
			if(times >= getConfig().getInt("kick.times")) { //And kick him when he reach the limit
				MetadataAccessor.setMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, times);
				String msg = formatMessage(evt.getPlayer(), curse, getConfig().getStringList("kick.messages"));
				aq.addAction(new ActionQueue.Kick(msg));
			} else {
				MetadataAccessor.setMetadata(this, evt.getPlayer(), CURSE_TIME_KEY, 0);
			}
		}
	}

	public void doReplace(AsyncPlayerChatEvent evt, String curse, ActionQueue aq) {
		evt.setCancelled(false);
		
		String goodWord = formatMessage(evt.getPlayer(), curse, getConfig().getStringList("replace.by"));
		evt.setMessage(evt.getMessage().replace(curse, goodWord));

		aq.addAction(new ActionQueue.Sound("replace.sound"));
	}

	/*
	 * Helpers
	 */

	private String formatMessage(Player player, String curse, List<String> messages) {
		String msg = messages.get(r.nextInt(messages.size()));

		Map<String, String> values = new HashMap<String, String>();
		values.put("player", player.getDisplayName());
		values.put("curse", curse);

		return StringHandler.translate(msg, values);
	}

		
	private static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

}
