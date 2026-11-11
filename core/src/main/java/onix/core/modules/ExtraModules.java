package onix.core.modules;

import onix.core.Onix;
import onix.core.module.Module;
import onix.core.setting.Setting;

/** Chat, effect and view modules whose work is done by a platform hook reading these options. */
public final class ExtraModules {
	private ExtraModules() {
	}

	/** Chat without the dark boxes behind each line, and with a much longer scrollback. */
	public static final class Chat extends Module {
		public final Setting.Bool clearBackground = add(new Setting.Bool("Clear Background", true)
				.note("Removes the dark box behind chat lines."));
		public final Setting.Number history = add(new Setting.Number("History Length", 500f, 100f, 2000f, 100f)
				.note("How many lines you can scroll back. Vanilla keeps 100."));

		public Chat() {
			super("Chat", "A cleaner chat with a longer history.");
		}

		/** Lines the chat may keep; the vanilla 100 while the module is off. */
		public int historyLength() {
			return isEnabled() ? Math.round(history.v) : 100;
		}
	}

	/** Recolors the shimmer on enchanted items. */
	public static final class GlintColor extends Module {
		public final Setting.Color color = add(new Setting.Color("Glint Color", 0xFF2D9CDB));

		public GlintColor() {
			super("Glint Colorizer", "Changes the color of the enchantment glint.");
		}
	}

	/** "Sharpness 5" instead of "Sharpness V" in item tooltips. */
	public static final class NumericalEnchants extends Module {
		public NumericalEnchants() {
			super("Numerical Enchantments", "Shows enchantment levels as numbers instead of Roman numerals.");
		}
	}

	/** More of the stars that fly off a critical or enchanted hit. Purely visual. */
	public static final class ParticleMultiplier extends Module {
		public final Setting.Number amount = add(new Setting.Number("Multiplier", 3f, 1f, 10f, 1f));
		public final Setting.Bool always = add(new Setting.Bool("Sharpness Particles", true)
				.note("Also multiply the particles of enchanted weapons."));

		public ParticleMultiplier() {
			super("Particle Multiplier", "Shows more critical hit particles.");
		}

		public int multiplier() {
			return isEnabled() ? Math.round(amount.v) : 1;
		}
	}

	/** Your own name tag above your head in third person. */
	public static final class OwnNametag extends Module {
		public OwnNametag() {
			super("Third Person Nametag", "Shows your own name tag in third person.");
		}
	}

	/** Hold a key to look at yourself from behind or from the front; release to go back. */
	public static final class Perspective extends Module {
		public final Setting.Key backKey = add(new Setting.Key("Behind Key", 0));
		public final Setting.Key frontKey = add(new Setting.Key("Front Key", 0));

		public Perspective() {
			super("Snaplook", "Hold a key to see yourself from behind or from the front.");
		}

		/** The view to force this frame: 1 behind, 2 front, or -1 for none. */
		public int wanted() {
			if (!isEnabled()) return -1;
			if (backKey.code != 0 && Onix.get().platform.isDown(backKey.code)) return 1;
			if (frontKey.code != 0 && Onix.get().platform.isDown(frontKey.code)) return 2;
			return -1;
		}
	}
}
