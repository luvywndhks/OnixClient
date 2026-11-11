package onix.core.modules;

import onix.core.Onix;
import onix.core.anim.Anim;
import onix.core.module.Module;
import onix.core.setting.Setting;

/**
 * Hold a key to zoom in. The platform's field-of-view hook multiplies the
 * game's FOV by {@link #fovScale()} every frame.
 */
public final class ZoomModule extends Module {
	public final Setting.Key zoomKey;
	private final Setting.Number amount = add(new Setting.Number("Zoom Amount", 4f, 1.5f, 10f, 0.5f));
	private final Setting.Bool smooth = add(new Setting.Bool("Smooth Zoom", true));
	private final Anim progress = new Anim(0f, 14f);
	private long last = System.nanoTime();

	public ZoomModule(int defaultKey) {
		super("Zoom", "Hold a key to look at things far away.");
		zoomKey = add(new Setting.Key("Zoom Key", defaultKey));
	}

	/** 1 when not zooming; smaller while the key is held. Call once per frame. */
	public float fovScale() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		boolean held = isEnabled() && zoomKey.code != 0 && Onix.get().platform.isDown(zoomKey.code);
		float t;
		if (smooth.on) {
			t = progress.update(held ? 1f : 0f, dt);
		} else {
			t = held ? 1f : 0f;
			progress.snap(t);
		}
		if (t <= 0f) return 1f;
		return 1f / (1f + (amount.v - 1f) * t);
	}
}
