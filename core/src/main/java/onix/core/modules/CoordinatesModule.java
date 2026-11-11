package onix.core.modules;

import onix.core.Onix;
import onix.core.platform.Platform;

public final class CoordinatesModule extends TextHudModule {
	public CoordinatesModule() {
		super("Coordinates", "Shows the block you are standing on.", 0.06f, 0.05f);
	}

	@Override
	protected boolean visible() {
		return Onix.get().platform.inWorld();
	}

	@Override
	protected long valueKey() {
		Platform p = Onix.get().platform;
		long x = (long) Math.floor(p.playerX()), y = (long) Math.floor(p.playerY()), z = (long) Math.floor(p.playerZ());
		return (x & 0x3FFFFFFL) << 38 | (z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
	}

	@Override
	protected String format() {
		Platform p = Onix.get().platform;
		return (long) Math.floor(p.playerX()) + ", " + (long) Math.floor(p.playerY()) + ", " + (long) Math.floor(p.playerZ());
	}
}
