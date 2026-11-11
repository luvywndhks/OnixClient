package onix.core.modules;

import onix.core.Onix;
import onix.core.input.ClickTracker;
import onix.core.setting.Setting;

public final class CpsModule extends TextHudModule {
	private final Setting.Bool right = add(new Setting.Bool("Show Right Click", true));

	public CpsModule() {
		super("CPS Counter", "Shows your clicks per second.", 0.955f, 0.26f);
	}

	@Override
	protected long valueKey() {
		ClickTracker c = Onix.get().clicks;
		return c.cps(0) * 1000L + c.cps(1) + (right.on ? 1000000L : 0L);
	}

	@Override
	protected String format() {
		ClickTracker c = Onix.get().clicks;
		return right.on ? c.cps(0) + " | " + c.cps(1) + " CPS" : c.cps(0) + " CPS";
	}
}
