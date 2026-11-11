package onix.core.modules;

import onix.core.module.Module;
import onix.core.setting.Setting;

/**
 * First-person animation modules. These only hold the options; the held-item
 * hooks of each platform read them while drawing the hand.
 */
public final class AnimationModules {
	private AnimationModules() {
	}

	/** Brings back how 1.7 drew the hand while an item is in use. */
	public static final class OldAnimations extends Module {
		public final Setting.Bool blockHit = add(new Setting.Bool("Block Hitting", true)
				.note("Swing your sword while blocking."));
		public final Setting.Bool eating = add(new Setting.Bool("Eating and Drinking Swing", true));
		public final Setting.Bool bow = add(new Setting.Bool("Bow Swing", true));
		public final Setting.Bool punching = add(new Setting.Bool("Punching During Usage", true)
				.note("Shows the swing when you click while using an item. Visual only."));

		public OldAnimations() {
			super("1.7 Animations", "The classic 1.7 first-person animations.");
		}
	}

	/** The old Lunar Client hand poses. */
	public static final class LunarAnimation extends Module {
		public final Setting.Bool blockHit = add(new Setting.Bool("Block Hit Pose", true));
		public final Setting.Bool itemPositions = add(new Setting.Bool("Item Positions", true)
				.note("Sword, potion, rod, bow, carpet and snow positions."));

		public LunarAnimation() {
			super("Lunar Old Animation", "The blocking pose and item positions of old Lunar Client.");
		}
	}

}
