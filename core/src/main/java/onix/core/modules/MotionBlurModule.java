package onix.core.modules;

import onix.core.module.Module;
import onix.core.setting.Setting;

/**
 * Motion blur in two versions. The drawing is done by the platform; this
 * class holds the options and turns them into the numbers the effect needs.
 *
 * V1 blends each frame into the next, so movement leaves a long soft trail.
 * V2 only mixes in clean, unblurred frames, so the trail stops after a frame
 * or a few and never smears; this is the approach of Latite Client.
 */
public final class MotionBlurModule extends Module {
	/** Matches the constants of the platform effect: 0 trail, 1 single frame, 2 pixel average. */
	public static final int TRAIL = 0, SINGLE = 1, AVERAGE = 2;

	private final Setting.Mode version = add(new Setting.Mode("Version", "V1", "V1", "V2"));
	private final Setting.Number trail = add(new Setting.Number("Strength", 5f, 1f, 10f, 1f).section("V1 Settings")
			.note("How long the trail lasts. The same at any frame rate."));
	private final Setting.Bool pixelAverage = add(new Setting.Bool("Pixel Average", true).section("V2 Settings")
			.note("Off blends just the previous frame. On blends a few, older ones fainter."));
	private final Setting.Number frames = add(new Setting.Number("Intensity", 5f, 2f, 8f, 1f).section("V2 Settings")
			.note("How many past frames Pixel Average uses."));
	private final Setting.Number opacity = add(new Setting.Number("Opacity", 5f, 2f, 16f, 1f).section("V2 Settings"));

	public MotionBlurModule() {
		super("Motion Blur", "Blurs the world while you move. V1 leaves a trail, V2 stays clean.");
	}

	public int mode() {
		if (version.index == 0) return TRAIL;
		return pixelAverage.on ? AVERAGE : SINGLE;
	}

	/** How much of the old picture is mixed in, 0 to 1. */
	public float strength() {
		// V1: 1..10 maps to 0.30..0.93 of the previous frame kept at 60 FPS.
		if (version.index == 0) return 0.23f + trail.v * 0.07f;
		// V2 uses the same scales as Latite: opacity / 10 for the average, / 12 for a single frame.
		return Math.min(1f, opacity.v / (pixelAverage.on ? 10f : 12f));
	}

	public int frames() {
		return Math.round(frames.v);
	}
}
