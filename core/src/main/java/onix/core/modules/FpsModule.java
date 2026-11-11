package onix.core.modules;

import onix.core.Onix;

public final class FpsModule extends TextHudModule {
	public FpsModule() {
		super("FPS Counter", "Shows your frames per second.", 0.955f, 0.2f);
	}

	@Override
	protected long valueKey() {
		return Onix.get().platform.fps();
	}

	@Override
	protected String format() {
		return Onix.get().platform.fps() + " FPS";
	}
}
