package onix.core.modules;

import java.util.Calendar;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.platform.Platform;
import onix.core.setting.Setting;

/** The small one-line readouts. Each is a few lines on top of {@link TextHudModule}. */
public final class InfoModules {
	private InfoModules() {
	}

	private static Platform p() {
		return Onix.get().platform;
	}

	public static final class Clock extends TextHudModule {
		private final Setting.Mode format = add(new Setting.Mode("Format", "12 Hour", "12 Hour", "24 Hour"));
		private final Calendar calendar = Calendar.getInstance();

		public Clock() {
			super("Clock", "Shows the time of your computer.", 0.5f, 0.03f);
		}

		@Override
		protected long valueKey() {
			return System.currentTimeMillis() / 60000L * 2L + format.index;
		}

		@Override
		protected String format() {
			calendar.setTimeInMillis(System.currentTimeMillis());
			int hour = calendar.get(Calendar.HOUR_OF_DAY), minute = calendar.get(Calendar.MINUTE);
			String mm = minute < 10 ? "0" + minute : Integer.toString(minute);
			if (format.index == 1) return (hour < 10 ? "0" : "") + hour + ":" + mm;
			int h = hour % 12 == 0 ? 12 : hour % 12;
			return h + ":" + mm + (hour < 12 ? " AM" : " PM");
		}
	}

	public static final class Ping extends TextHudModule {
		public Ping() {
			super("Ping Display", "Shows your latency to the server.", 0.955f, 0.32f);
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return p().ping();
		}

		@Override
		protected String format() {
			int ping = p().ping();
			return ping < 0 ? "0 ms" : ping + " ms";
		}
	}

	public static final class ServerIp extends TextHudModule {
		private String last = "";

		public ServerIp() {
			super("Server IP", "Shows the address of the server you are on.", 0.5f, 0.08f);
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			String address = p().serverAddress();
			last = address == null ? "Singleplayer" : address;
			return last.hashCode();
		}

		@Override
		protected String format() {
			return last;
		}
	}

	public static final class Speed extends TextHudModule {
		private final Setting.Bool vertical = add(new Setting.Bool("Include Vertical", false));
		private double lastX, lastY, lastZ;
		private float speed;
		private boolean primed;

		public Speed() {
			super("Speed Display", "Shows how fast you are moving, in blocks per second.", 0.955f, 0.38f);
			on(Events.Tick.class, new Listener<Events.Tick>() {
				@Override
				public void on(Events.Tick e) {
					Platform p = p();
					double x = p.playerX(), y = p.playerY(), z = p.playerZ();
					if (primed) {
						double dx = x - lastX, dy = vertical.on ? y - lastY : 0, dz = z - lastZ;
						double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
						// A jump of this size in one tick is a teleport, not movement.
						speed = moved > 20 ? 0f : (float) (moved * 20.0);
					}
					lastX = x;
					lastY = y;
					lastZ = z;
					primed = true;
				}
			});
		}

		@Override
		protected void onEnable() {
			primed = false;
			speed = 0f;
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return Math.round(speed * 10f);
		}

		@Override
		protected String format() {
			int tenths = Math.round(speed * 10f);
			return tenths / 10 + "." + tenths % 10 + " m/s";
		}
	}

	public static final class Direction extends TextHudModule {
		private static final String[] NAMES = { "South", "South West", "West", "North West", "North", "North East", "East", "South East" };
		private final Setting.Bool degrees = add(new Setting.Bool("Show Degrees", false));

		public Direction() {
			super("Direction HUD", "Shows which way you are facing.", 0.5f, 0.13f);
		}

		private int normalised() {
			float yaw = p().yaw() % 360f;
			if (yaw < 0f) yaw += 360f;
			return Math.round(yaw) % 360;
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			int deg = normalised();
			return degrees.on ? deg + 1000L : ((deg + 22) / 45) % 8;
		}

		@Override
		protected String format() {
			int deg = normalised();
			String name = NAMES[((deg + 22) / 45) % 8];
			return degrees.on ? name + " (" + deg + "°)" : name;
		}
	}

	public static final class DayCounter extends TextHudModule {
		public DayCounter() {
			super("Day Counter", "Shows how many days the world has existed.", 0.06f, 0.11f);
		}

		@Override
		protected boolean visible() {
			return p().inWorld();
		}

		@Override
		protected long valueKey() {
			return p().timeOfDay() / 24000L;
		}

		@Override
		protected String format() {
			return "Day " + (p().timeOfDay() / 24000L);
		}
	}

	public static final class Memory extends TextHudModule {
		public Memory() {
			super("Memory Display", "Shows how much memory the game is using.", 0.06f, 0.17f);
		}

		private static long percent() {
			Runtime rt = Runtime.getRuntime();
			return (rt.totalMemory() - rt.freeMemory()) * 100L / rt.maxMemory();
		}

		@Override
		protected long valueKey() {
			return percent();
		}

		@Override
		protected String format() {
			Runtime rt = Runtime.getRuntime();
			long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
			return percent() + "% (" + used + " MB)";
		}
	}
}
