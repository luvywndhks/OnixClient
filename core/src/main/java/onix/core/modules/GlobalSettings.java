package onix.core.modules;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.Module;
import onix.core.platform.Platform;
import onix.core.setting.Setting;

/**
 * The first page of the menu. Always on. Besides the client options it holds
 * the small fixes and performance switches that are not worth a module each;
 * the platform hooks read these fields directly.
 */
public final class GlobalSettings extends Module {
	public final Setting.Key menuKey;
	public final Setting.Bool blur = add(new Setting.Bool("Blurred Menu Background", true));
	public final Setting.Bool animations = add(new Setting.Bool("Animations", true));
	public final Setting.Mode hudFont = add(new Setting.Mode("HUD Font", "Onix", "Onix", "Minecraft")
			.note("The font used by every HUD module."));

	public final Setting.Bool noBreakParticles = add(new Setting.Bool("Disable Block Break Particles", false).section("Performance"));
	public final Setting.Bool noWeather = add(new Setting.Bool("Disable Weather Rendering", false).section("Performance"));
	public final Setting.Bool noVignette = add(new Setting.Bool("Disable Vignette", false).section("Performance"));
	public final Setting.Bool noGlint = add(new Setting.Bool("Disable Enchantment Glint", false).section("Performance"));
	public final Setting.Bool backgroundFps = add(new Setting.Bool("Limit FPS In Background", true).section("Performance"));
	public final Setting.Number backgroundFpsLimit = add(new Setting.Number("Background FPS", 15f, 1f, 60f, 1f).section("Performance")
			.note("Used while the game window is not focused."));

	public final Setting.Bool hitDelayFix = add(new Setting.Bool("Hit Delay Fix", false).section("Tweaks")
			.note("Removes the attack lockout after a missed hit, as in 1.7."));
	public final Setting.Bool staticFov = add(new Setting.Bool("Static FOV", false).section("Tweaks"));
	public final Setting.Bool noHurtCam = add(new Setting.Bool("No Hurt Cam", false).section("Tweaks"));
	public final Setting.Bool lowFire = add(new Setting.Bool("Low Fire Overlay", false).section("Tweaks"));
	public final Setting.Bool noPumpkin = add(new Setting.Bool("Hide Pumpkin Overlay", false).section("Tweaks"));
	public final Setting.Bool noStuckArrows = add(new Setting.Bool("Hide Arrows Stuck In Players", false).section("Tweaks"));
	public final Setting.Bool hideScoreboard = add(new Setting.Bool("Hide Scoreboard", false).section("Tweaks"));

	private int savedFpsLimit = -1;

	public GlobalSettings(int defaultMenuKey) {
		super("Global Settings", "Change global settings for the client.");
		toggleable = false;
		settings.remove(key);
		menuKey = new Setting.Key("Open Client Key", defaultMenuKey);
		settings.add(0, menuKey);
		on(Events.Tick.class, new Listener<Events.Tick>() {
			@Override
			public void on(Events.Tick e) {
				limitBackgroundFps();
			}
		});
	}

	/** Caps the frame rate while the window is in the background, and puts it back on return. */
	private void limitBackgroundFps() {
		Platform p = Onix.get().platform;
		boolean limit = backgroundFps.on && !p.windowFocused();
		if (limit && savedFpsLimit < 0) {
			savedFpsLimit = p.fpsLimit();
			p.setFpsLimit(Math.round(backgroundFpsLimit.v));
		} else if (!limit && savedFpsLimit >= 0) {
			p.setFpsLimit(savedFpsLimit);
			savedFpsLimit = -1;
		}
	}
}
