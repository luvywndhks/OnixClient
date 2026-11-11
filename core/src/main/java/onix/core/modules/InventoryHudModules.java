package onix.core.modules;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.HudModule;
import onix.core.platform.Platform;
import onix.core.render.Renderer;
import onix.core.setting.Setting;

/** Inventory and resource readouts, plus server TPS and session time. */
public final class InventoryHudModules {
	private InventoryHudModules() {
	}

	private static Platform p() {
		return Onix.get().platform;
	}

	/** The 27 backpack slots as a grid, so you can check supplies without opening the inventory. */
	public static final class Inventory extends HudModule {
		private static final float CELL = 32f, PAD = 8f, COUNT = 13f;
		private final Setting.Bool hideEmpty = add(new Setting.Bool("Hide When Empty", false));
		private final String[] numbers = new String[65];

		public Inventory() {
			super("Inventory HUD", "Shows the contents of your inventory.", 0.5f, 0.82f);
		}

		@Override
		protected boolean visible() {
			if (!p().inWorld()) return false;
			if (!hideEmpty.on) return true;
			for (int i = 0; i < 27; i++) {
				if (p().itemCount(Platform.INVENTORY + 9 + i) > 0) return true;
			}
			return false;
		}

		@Override
		protected float contentWidth(Renderer r) {
			return CELL * 9f + PAD * 2f;
		}

		@Override
		protected float contentHeight(Renderer r) {
			return CELL * 3f + PAD * 2f;
		}

		@Override
		protected void draw(Renderer r, float x, float y, float s) {
			Platform p = p();
			float size = COUNT * s;
			for (int i = 0; i < 27; i++) {
				int slot = Platform.INVENTORY + 9 + i;
				int count = p.itemCount(slot);
				if (count <= 0) continue;
				float cx = x + (PAD + (i % 9) * CELL) * s, cy = y + (PAD + (i / 9) * CELL) * s;
				r.item(slot, cx + 2f * s, cy + 2f * s, (CELL - 4f) * s);
				if (count > 1) {
					String text = number(count);
					// Stack sizes sit over the icon, so they are drawn on the layer above items.
					r.topLayer(true);
					r.text(text, cx + CELL * s - r.textWidth(text, size, Renderer.MEDIUM) - 1f * s,
							cy + CELL * s - r.lineHeight(size) + 1f * s, size, textColor.argb, Renderer.MEDIUM);
					r.topLayer(false);
				}
			}
		}

		private String number(int count) {
			if (count < 0 || count >= numbers.length) return Integer.toString(count);
			if (numbers[count] == null) numbers[count] = Integer.toString(count);
			return numbers[count];
		}
	}

	public static final class Saturation extends TextHudModule {
		public Saturation() {
			super("Saturation Display", "Shows your hidden saturation level.", 0.6f, 0.9f);
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return Math.round(p().saturation() * 10f);
		}

		@Override
		protected String format() {
			int tenths = Math.round(p().saturation() * 10f);
			return "Saturation: " + tenths / 10 + "." + tenths % 10;
		}
	}

	public static final class ArrowCounter extends TextHudModule {
		private final Setting.Bool onlyWithBow = add(new Setting.Bool("Hide Without Arrows", false));

		public ArrowCounter() {
			super("Arrow Counter", "Shows how many arrows you have.", 0.4f, 0.9f);
		}

		@Override
		protected boolean visible() {
			return p().inWorld() && (!onlyWithBow.on || p().countItems(Platform.ARROWS) > 0);
		}

		@Override
		protected long valueKey() {
			return p().countItems(Platform.ARROWS);
		}

		@Override
		protected String format() {
			int n = p().countItems(Platform.ARROWS);
			return n + (n == 1 ? " Arrow" : " Arrows");
		}
	}

	/** Healing left in the inventory: splash potions of healing, or mushroom stew. */
	public static final class PotionCounter extends TextHudModule {
		private final Setting.Mode type = add(new Setting.Mode("Count", "Potions", "Potions", "Soup", "Both"));

		public PotionCounter() {
			super("Potion/Soup Counter", "Shows how many healing potions or soups you have left.", 0.4f, 0.95f);
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return p().countItems(Platform.HEAL_POTIONS) * 1000L + p().countItems(Platform.SOUPS) + type.index * 1000000L;
		}

		@Override
		protected String format() {
			int pots = p().countItems(Platform.HEAL_POTIONS), soup = p().countItems(Platform.SOUPS);
			if (type.index == 0) return pots + (pots == 1 ? " Pot" : " Pots");
			if (type.index == 1) return soup + (soup == 1 ? " Soup" : " Soups");
			return pots + " Pots | " + soup + " Soup";
		}
	}

	/**
	 * Server ticks per second. The server sends its clock once every 20 ticks,
	 * so the gap between two of those packets shows how fast it is running.
	 * The value is averaged, and a long silence is reported as lag.
	 */
	public static final class Tps extends TextHudModule {
		private static final int SAMPLES = 6;
		private final float[] samples = new float[SAMPLES];
		private int count, next;
		private long lastPacket;

		public Tps() {
			super("TPS Display", "Shows how fast the server is running.", 0.955f, 0.44f);
			on(Events.ServerTime.class, new Listener<Events.ServerTime>() {
				@Override
				public void on(Events.ServerTime e) {
					long now = System.nanoTime();
					if (lastPacket != 0L) {
						float seconds = (now - lastPacket) / 1e9f;
						// Packets bunch up after a lag spike; ignore gaps too short to mean anything.
						if (seconds > 0.2f) {
							samples[next] = Math.min(20f, 20f / seconds);
							next = (next + 1) % SAMPLES;
							if (count < SAMPLES) count++;
						}
					}
					lastPacket = now;
				}
			});
		}

		@Override
		protected void onEnable() {
			count = 0;
			next = 0;
			lastPacket = 0L;
		}

		private float average() {
			if (count == 0) return 20f;
			float sum = 0f;
			for (int i = 0; i < count; i++) sum += samples[i];
			return sum / count;
		}

		private boolean stalled() {
			return lastPacket != 0L && System.nanoTime() - lastPacket > 3000000000L;
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return stalled() ? -1L : Math.round(average() * 10f);
		}

		@Override
		protected String format() {
			if (stalled()) return "Server Lagging";
			int tenths = Math.round(average() * 10f);
			return tenths / 10 + "." + tenths % 10 + " TPS";
		}
	}

	/** Time since the game started. */
	public static final class Playtime extends TextHudModule {
		private final long start = System.currentTimeMillis();

		public Playtime() {
			super("Playtime", "Shows how long you have been playing this session.", 0.06f, 0.23f);
		}

		@Override
		protected long valueKey() {
			return (System.currentTimeMillis() - start) / 1000L;
		}

		@Override
		protected String format() {
			long t = (System.currentTimeMillis() - start) / 1000L;
			long h = t / 3600L, m = t / 60L % 60L, sec = t % 60L;
			return (h > 0 ? h + ":" + (m < 10 ? "0" : "") : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
		}
	}
}
