package onix.core.modules;

import java.util.Locale;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.Module;
import onix.core.setting.Setting;

public final class ConvenienceModules {
	private ConvenienceModules() {
	}

	/**
	 * Says "gg" for you when a game ends. A game counts as over when a chat line
	 * contains one of the end-of-game phrases servers print; a line that a player
	 * typed is ignored, so nobody can trigger it by chatting.
	 */
	public static final class AutoGG extends Module {
		private static final String[] TRIGGERS = { "1st killer - ", "1st place - ", "winner: ", "winners: ", "winner - ",
				"winning team - ", " won the game", "reward summary", "you won! want to play again", "you died! want to play again",
				"game over!", "damage dealt - " };

		private final Setting.Mode message = add(new Setting.Mode("Message", "gg", "gg", "GG", "gg wp", "Good game"));
		private final Setting.Number delay = add(new Setting.Number("Delay (seconds)", 1f, 0f, 5f, 0.5f));
		private int ticksUntilSend = -1;
		private int cooldown;

		public AutoGG() {
			super("AutoGG", "Says gg automatically when a game ends.");
			on(Events.Chat.class, new Listener<Events.Chat>() {
				@Override
				public void on(Events.Chat e) {
					if (cooldown > 0 || ticksUntilSend >= 0 || !isGameEnd(e.text)) return;
					ticksUntilSend = Math.round(delay.v * 20f);
				}
			});
			on(Events.Tick.class, new Listener<Events.Tick>() {
				@Override
				public void on(Events.Tick e) {
					if (cooldown > 0) cooldown--;
					if (ticksUntilSend < 0) return;
					if (ticksUntilSend-- > 0) return;
					ticksUntilSend = -1;
					// One gg per game: nothing more for 15 seconds.
					cooldown = 300;
					Onix.get().platform.sendChat(message.get());
				}
			});
		}

		static boolean isGameEnd(String line) {
			// "Name: text" and "[RANK] Name: text" are players talking, not the server.
			int colon = line.indexOf(": ");
			String lower = line.toLowerCase(Locale.ROOT).trim();
			if (colon > 0 && colon < 40 && !lower.startsWith("winner") && !lower.startsWith("winners")) return false;
			for (String trigger : TRIGGERS) {
				if (lower.contains(trigger)) return true;
			}
			return false;
		}
	}

	/** Shows another name in place of yours, on your screen only. Useful when recording. */
	public static final class NickHider extends Module {
		private final Setting.Mode replacement = add(new Setting.Mode("Show As", "You", "You", "Player", "Hidden", "Onix User"));
		private String name, lastInput, lastOutput;

		public NickHider() {
			super("Nick Hider", "Hides your name on your own screen.");
		}

		/** Called by the text hook for every string the game draws while the module is on. */
		public String apply(String text) {
			if (text == null) return null;
			if (name == null) name = Onix.get().platform.username();
			if (name == null || name.isEmpty() || text.indexOf(name) < 0) return text;
			// The same lines are drawn every frame, so remember the last replacement.
			if (text.equals(lastInput)) return lastOutput;
			lastInput = text;
			lastOutput = text.replace(name, replacement.get());
			return lastOutput;
		}

		@Override
		protected void onEnable() {
			name = null;
			lastInput = null;
		}
	}
}
