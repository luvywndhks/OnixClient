package onix.core.modules;

import java.util.ArrayList;
import java.util.List;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.HudModule;
import onix.core.platform.Platform;
import onix.core.render.Renderer;
import onix.core.setting.Setting;

/** The PvP readouts: armor, potion effects, combo and reach. */
public final class CombatHudModules {
	private CombatHudModules() {
	}

	private static Platform p() {
		return Onix.get().platform;
	}

	/**
	 * Armor pieces and the held item, each with what is left of its durability.
	 * The number turns yellow, then red, as the piece wears out.
	 */
	public static final class Armor extends HudModule {
		private static final float ICON = 30f, GAP = 4f, PAD = 8f, TEXT = 17f;
		private final Setting.Bool held = add(new Setting.Bool("Show Held Item", true));
		private final Setting.Mode style = add(new Setting.Mode("Durability", "Remaining", "Remaining", "Percent", "Hidden"));
		private final Setting.Bool horizontal = add(new Setting.Bool("Horizontal", false));

		// Slots 0..3 are helmet to boots, 4 is the held item.
		private final String[] text = new String[5];
		private final long[] textKey = new long[5];
		private final int[] shown = new int[5];
		private int count;

		public Armor() {
			super("Armor HUD", "Shows your armor and how worn it is.", 0.04f, 0.45f);
			java.util.Arrays.fill(textKey, Long.MIN_VALUE);
		}

		private void collect() {
			count = 0;
			Platform p = p();
			int last = held.on ? 5 : 4;
			for (int slot = 0; slot < last; slot++) {
				if (p.itemDurability(slot) != Platform.NO_ITEM) shown[count++] = slot;
			}
		}

		private String label(int slot) {
			Platform p = p();
			int left = p.itemDurability(slot), max = p.itemMaxDurability(slot), stack = p.itemCount(slot);
			long key = ((long) left << 32) ^ ((long) stack << 8) ^ style.index;
			if (key != textKey[slot]) {
				textKey[slot] = key;
				if (left == Platform.UNBREAKABLE) text[slot] = stack > 1 ? Integer.toString(stack) : "";
				else if (style.index == 1) text[slot] = (max <= 0 ? 100 : Math.round(left * 100f / max)) + "%";
				else text[slot] = Integer.toString(left);
			}
			return text[slot];
		}

		private int color(int slot) {
			Platform p = p();
			int left = p.itemDurability(slot), max = p.itemMaxDurability(slot);
			if (left < 0 || max <= 0) return textColor.argb;
			float f = left / (float) max;
			return f > 0.5f ? textColor.argb : f > 0.2f ? 0xFFF2C94C : 0xFFEB5757;
		}

		@Override
		protected boolean visible() {
			if (!p().inWorld()) return false;
			collect();
			return count > 0;
		}

		private float textWidth(Renderer r) {
			if (style.index == 2) return 0f;
			float w = 0f;
			for (int i = 0; i < count; i++) w = Math.max(w, r.textWidth(label(shown[i]), TEXT, Renderer.REGULAR));
			return w > 0f ? w + 8f : 0f;
		}

		@Override
		protected float contentWidth(Renderer r) {
			float cell = ICON + textWidth(r);
			return PAD * 2f + (horizontal.on ? count * cell + (count - 1) * GAP : cell);
		}

		@Override
		protected float contentHeight(Renderer r) {
			return PAD * 2f + (horizontal.on ? ICON : count * ICON + (count - 1) * GAP);
		}

		@Override
		protected void draw(Renderer r, float x, float y, float s) {
			float cell = ICON + textWidth(r);
			for (int i = 0; i < count; i++) {
				int slot = shown[i];
				float cx = x + (PAD + (horizontal.on ? i * (cell + GAP) : 0f)) * s;
				float cy = y + (PAD + (horizontal.on ? 0f : i * (ICON + GAP))) * s;
				r.item(slot, cx, cy, ICON * s);
				if (style.index != 2) {
					float size = TEXT * s;
					r.text(label(slot), cx + (ICON + 6f) * s, cy + (ICON * s - r.lineHeight(size)) / 2f, size, color(slot), Renderer.REGULAR);
				}
			}
		}
	}

	/** Active potion effects with their time left; an effect about to run out blinks. */
	public static final class Potions extends HudModule {
		private static final float SIZE = 18f, PAD = 10f, LINE_GAP = 4f;
		private final Setting.Bool blink = add(new Setting.Bool("Blink When Ending", true));
		private final List<String> lines = new ArrayList<String>();
		private final List<Integer> ticksLeft = new ArrayList<Integer>();

		public Potions() {
			super("Potion HUD", "Shows your potion effects and how long they last.", 0.04f, 0.65f);
			on(Events.Tick.class, new Listener<Events.Tick>() {
				@Override
				public void on(Events.Tick e) {
					// Durations only change once per tick, so the text is rebuilt per tick, not per frame.
					p().effects(lines, ticksLeft);
				}
			});
		}

		@Override
		protected void onDisable() {
			lines.clear();
			ticksLeft.clear();
		}

		@Override
		protected boolean visible() {
			return p().inWorld() && !lines.isEmpty();
		}

		@Override
		protected float contentWidth(Renderer r) {
			float w = 0f;
			for (int i = 0; i < lines.size(); i++) w = Math.max(w, r.textWidth(lines.get(i), SIZE, Renderer.REGULAR));
			return w + PAD * 2f;
		}

		@Override
		protected float contentHeight(Renderer r) {
			int n = lines.size();
			return PAD * 2f + n * r.lineHeight(SIZE) + (n - 1) * LINE_GAP;
		}

		@Override
		protected void draw(Renderer r, float x, float y, float s) {
			float size = SIZE * s, step = r.lineHeight(size) + LINE_GAP * s;
			boolean dim = (System.nanoTime() / 300000000L) % 2 == 0;
			for (int i = 0; i < lines.size(); i++) {
				boolean ending = blink.on && ticksLeft.get(i) < 200;
				int color = ending && dim ? Onix.get().theme.withAlpha(textColor.argb, 0.45f) : textColor.argb;
				r.text(lines.get(i), x + PAD * s, y + PAD * s + i * step, size, color, Renderer.REGULAR);
			}
		}
	}

	/**
	 * Counts hits landed in a row. The combo ends when you are hit, or after
	 * the timeout with no new hit.
	 */
	public static final class Combo extends TextHudModule {
		private final Setting.Number timeout = add(new Setting.Number("Reset After (seconds)", 2f, 1f, 5f, 0.5f));
		private int combo;
		private int ticksSinceHit;

		public Combo() {
			super("Combo Counter", "Counts the hits you land in a row.", 0.5f, 0.6f);
			on(Events.HitLanded.class, new Listener<Events.HitLanded>() {
				@Override
				public void on(Events.HitLanded e) {
					combo++;
					ticksSinceHit = 0;
				}
			});
			on(Events.PlayerHurt.class, new Listener<Events.PlayerHurt>() {
				@Override
				public void on(Events.PlayerHurt e) {
					combo = 0;
				}
			});
			on(Events.Tick.class, new Listener<Events.Tick>() {
				@Override
				public void on(Events.Tick e) {
					if (combo > 0 && ++ticksSinceHit > timeout.v * 20f) combo = 0;
				}
			});
		}

		@Override
		protected void onEnable() {
			combo = 0;
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return combo;
		}

		@Override
		protected String format() {
			return combo == 0 ? "No Combo" : combo + " Combo";
		}
	}

	/** How far away your last hit landed, in blocks. */
	public static final class Reach extends TextHudModule {
		private final Setting.Number hold = add(new Setting.Number("Show For (seconds)", 3f, 1f, 10f, 0.5f));
		private float reach;
		private int ticksSince = Integer.MAX_VALUE / 2;

		public Reach() {
			super("Reach Display", "Shows the distance of your last hit.", 0.5f, 0.66f);
			on(Events.Attack.class, new Listener<Events.Attack>() {
				@Override
				public void on(Events.Attack e) {
					reach = e.reach;
					ticksSince = 0;
				}
			});
			on(Events.Tick.class, new Listener<Events.Tick>() {
				@Override
				public void on(Events.Tick e) {
					if (ticksSince < Integer.MAX_VALUE / 2) ticksSince++;
				}
			});
		}

		private boolean fresh() {
			return ticksSince <= hold.v * 20f;
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return fresh() ? Math.round(reach * 100f) : -1L;
		}

		@Override
		protected String format() {
			if (!fresh()) return "0.00 blocks";
			int hundredths = Math.round(reach * 100f);
			int frac = hundredths % 100;
			return hundredths / 100 + "." + (frac < 10 ? "0" : "") + frac + " blocks";
		}
	}
}
