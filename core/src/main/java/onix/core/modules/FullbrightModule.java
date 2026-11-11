package onix.core.modules;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.Module;
import onix.core.setting.Setting;

/** Makes the surrounding environment brighter by raising gamma. */
public final class FullbrightModule extends Module {
	private final Setting.Number gamma = add(new Setting.Number("Gamma", 25f, 1f, 100f, 1f).section("Render Settings"));
	private float before = Float.NaN;
	private float applied = Float.NaN;

	public FullbrightModule() {
		super("Fullbright", "Makes the surrounding environment brighter.");
		on(Events.Tick.class, new Listener<Events.Tick>() {
			@Override
			public void on(Events.Tick e) {
				// Follows the slider while the module is on.
				if (applied != gamma.v) apply();
			}
		});
	}

	private void apply() {
		applied = gamma.v;
		Onix.get().platform.setGamma(applied);
	}

	@Override
	protected void onEnable() {
		before = Onix.get().platform.gamma();
		apply();
	}

	@Override
	protected void onDisable() {
		// Vanilla gamma tops out at 1. A higher saved value was ours from an earlier run.
		if (!Float.isNaN(before)) Onix.get().platform.setGamma(Math.min(before, 1f));
		before = Float.NaN;
		applied = Float.NaN;
	}
}
