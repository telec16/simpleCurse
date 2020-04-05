package fr.telec.simpleCurse;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.telec.simpleCore.SoundHelper;

public class ActionQueue {

	private static final String MESSAGE = "message";
	private static final String SOUND = "sound";
	private static final String HIT = "hit";
	private static final String KICK = "kick";
	private static final String DELAY = "delay";

	private interface Action {
		String getName();
	}

	public static class Message implements Action {
		public final String msg;

		public Message(String msg) {
			this.msg = msg;
		}

		@Override
		public String getName() {
			return MESSAGE;
		}

	}

	public static class Sound implements Action {
		public final String path;

		public Sound(String path) {
			this.path = path;
		}

		@Override
		public String getName() {
			return SOUND;
		}

	}

	public static class Hit implements Action {
		public final int damages;

		public Hit(int damages) {
			this.damages = damages;
		}

		@Override
		public String getName() {
			return HIT;
		}

	}

	public static class Kick implements Action {
		public final String reason;

		public Kick(String reason) {
			this.reason = reason;
		}

		@Override
		public String getName() {
			return KICK;
		}

	}

	public static class Delay implements Action {
		public final int delay;

		public Delay(int delay) {
			this.delay = delay;
		}

		@Override
		public String getName() {
			return DELAY;
		}

	}

	private JavaPlugin plugin;
	private SoundHelper sh;
	private AsyncPlayerChatEvent evt;
	private List<Action> actions;

	public ActionQueue(JavaPlugin plugin, SoundHelper sh, AsyncPlayerChatEvent evt) {
		this.plugin = plugin;
		this.evt = evt;
		this.sh = sh;

		actions = new ArrayList<Action>();
	}

	public void addAction(Action action) {
		actions.add(action);
	}

	public void executeActions() {
		if(actions.size() > 0) {
			Action action = actions.remove(0);
			int delay = 0;
			
			// Before anything, check if the player is still here
			if(evt.getPlayer().isOnline())
				return;
			
			if(action.getName() == DELAY)
				delay = ((Delay) action).delay;
			else
				executeAction(action);
			
			Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
				@Override
				public void run() {
					executeActions();
				}
			}, delay);
		}
	}

	public boolean executeAction(Action action) {
		switch (action.getName()) {
		case MESSAGE:
			sendMessage((Message) action);
			break;
		case SOUND:
			playSound((Sound) action);
			break;
		case HIT:
			hit((Hit) action);
			break;
		case KICK:
			kick((Kick) action);
			break;
			default:
				return false;
		}
		
		return true;
	}

	private void sendMessage(Message message) {
		evt.getPlayer().sendMessage(message.msg);
	}

	private void playSound(Sound sound) {
		sh.playFromConfig(evt.getPlayer(), sound.path);
	}

	private void hit(Hit hit) {
		Bukkit.getScheduler().runTask(plugin, new Runnable() {
			public void run() {
				evt.getPlayer().damage(hit.damages);
			}
		});
	}

	private void kick(Kick kick) {
		Bukkit.getScheduler().runTask(plugin, new Runnable() {
			public void run() {
				evt.getPlayer().kickPlayer(kick.reason);
			}
		});
	}

}
