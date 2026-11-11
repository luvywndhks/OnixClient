package onix.core.modules;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.Module;
import onix.core.platform.Platform;
import onix.core.render.Renderer;
import onix.core.setting.Setting;

/** Visual PvP modules. Several only hold options that a platform hook reads. */
public final class PvpModules {
	private PvpModules() {
	}

	/**
	 * Replaces the vanilla crosshair with one drawn by the client. It appears
	 * exactly when the vanilla one would, and can turn red over an entity.
	 */
	public static final class Crosshair extends Module {
		private final Setting.Mode style = add(new Setting.Mode("Crosshair", "Cross", "Cross", "Plus", "Dot", "Circle"));
		private final Setting.Number size = add(new Setting.Number("Size", 9f, 2f, 30f, 1f).section("Visual Settings"));
		private final Setting.Number thickness = add(new Setting.Number("Thickness", 2f, 1f, 8f, 1f).section("Visual Settings"));
		private final Setting.Number gap = add(new Setting.Number("Gap", 4f, 0f, 20f, 1f).section("Visual Settings"));
		private final Setting.Bool dot = add(new Setting.Bool("Center Dot", false).section("Visual Settings"));
		private final Setting.Bool outline = add(new Setting.Bool("Crosshair Outline", true).section("Visual Settings"));
		private final Setting.Color color = add(new Setting.Color("Color", 0xFFFFFFFF).section("Visual Settings"));
		private final Setting.Bool redOnEntity = add(new Setting.Bool("Red When Facing Entity", true).section("Display Settings"));
		private final Setting.Color entityColor = add(new Setting.Color("Entity Color", 0xFFEB5757).section("Display Settings"));

		public Crosshair() {
			super("Custom Crosshair", "Customize your crosshair to your hearts content.");
			on(Events.HudRender.class, new Listener<Events.HudRender>() {
				@Override
				public void on(Events.HudRender e) {
					Platform p = Onix.get().platform;
					if (!p.crosshairWanted()) return;
					int c = redOnEntity.on && p.lookingAtEntity() ? entityColor.argb : color.argb;
					draw(e.renderer, Math.round(e.width / 2f), Math.round(e.height / 2f), e.height / 1080f, c);
				}
			});
		}

		private void draw(Renderer r, float cx, float cy, float s, int c) {
			float len = Math.max(1f, Math.round(size.v * s)), th = Math.max(1f, Math.round(thickness.v * s));
			float g = style.index == 1 ? 0f : Math.round(gap.v * s);
			float o = outline.on ? Math.max(1f, Math.round(s)) : 0f;
			if (style.index == 2) {
				bar(r, cx - th, cy - th, th * 2f, th * 2f, th, c, o);
				return;
			}
			if (style.index == 3) {
				float rad = len + g;
				if (o > 0f) r.panel(cx - rad - o, cy - rad - o, (rad + o) * 2f, (rad + o) * 2f, rad + o, 0, th + o * 2f, 0xC0000000, false);
				r.panel(cx - rad, cy - rad, rad * 2f, rad * 2f, rad, 0, th, c, false);
			} else {
				float half = th / 2f;
				bar(r, cx - g - len, cy - half, len, th, 0f, c, o);
				bar(r, cx + g, cy - half, len, th, 0f, c, o);
				bar(r, cx - half, cy - g - len, th, len, 0f, c, o);
				bar(r, cx - half, cy + g, th, len, 0f, c, o);
			}
			if (dot.on) bar(r, cx - th / 2f, cy - th / 2f, th, th, 0f, c, o);
		}

		private static void bar(Renderer r, float x, float y, float w, float h, float radius, int c, float o) {
			if (o > 0f) r.panel(x - o, y - o, w + o * 2f, h + o * 2f, radius + (radius > 0f ? o : 0f), 0xC0000000, 0f, 0, false);
			r.panel(x, y, w, h, radius, c, 0f, 0, false);
		}
	}

	/** Changes the tint entities flash with when they are hit. */
	public static final class HurtColor extends Module {
		public final Setting.Color color = add(new Setting.Color("Color", 0xFFFF0000));
		public final Setting.Number strength = add(new Setting.Number("Strength", 0.3f, 0f, 1f, 0.05f)
				.note("Vanilla is 0.30."));

		public HurtColor() {
			super("Hurt Color", "Changes the color entities flash when hit.");
		}
	}

	/** Recolors and thickens the outline around the block you are looking at. */
	public static final class BlockOutline extends Module {
		public final Setting.Color color = add(new Setting.Color("Outline Color", 0xFF146B8C));
		public final Setting.Number opacity = add(new Setting.Number("Opacity", 1f, 0.1f, 1f, 0.05f));
		public final Setting.Number width = add(new Setting.Number("Line Width", 3f, 1f, 8f, 0.5f));

		public BlockOutline() {
			super("Block Outline/Overlay", "Customizes the outline of the block you are looking at.");
		}
	}

	/** Shows the world at a time of your choosing. Only you see it. */
	public static final class TimeChanger extends Module {
		private final Setting.Number time = add(new Setting.Number("Time", 6000f, 0f, 24000f, 500f)
				.note("0 sunrise, 6000 noon, 12000 sunset, 18000 midnight."));

		public TimeChanger() {
			super("Time Changer", "Changes the time of day on your screen only.");
			Listener<Object> apply = new Listener<Object>() {
				@Override
				public void on(Object e) {
					Onix.get().platform.setTimeOfDay((long) time.v);
				}
			};
			// The game advances time every tick and the server corrects it every second, so both are overridden.
			on(Events.Tick.class, cast(apply));
			on(Events.ServerTime.class, cast(apply));
		}

		@SuppressWarnings("unchecked")
		private static <E> Listener<E> cast(Listener<Object> l) {
			return (Listener<E>) (Listener<?>) l;
		}
	}

	/** Shows the weather of your choosing. Only you see it. */
	public static final class WeatherChanger extends Module {
		private final Setting.Mode weather = add(new Setting.Mode("Weather", "Clear", "Clear", "Rain", "Thunder"));

		public WeatherChanger() {
			super("Weather Changer", "Changes the weather on your screen only.");
			on(Events.Tick.class, new Listener<Events.Tick>() {
				@Override
				public void on(Events.Tick e) {
					Onix.get().platform.setWeather(weather.index >= 1 ? 1f : 0f, weather.index == 2 ? 1f : 0f);
				}
			});
		}
	}
}
