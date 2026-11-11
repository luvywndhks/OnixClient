package onix.core.modules;

import onix.core.Onix;
import onix.core.module.Module;
import onix.core.setting.Setting;

/**
 * Hold a key to look around in third person without turning your character.
 *
 * The camera angles live here; each platform's camera hooks feed mouse movement
 * into {@link #turn} and read {@link #yaw}/{@link #pitch} while {@link #looking}.
 * Servers that forbid it are listed in the constructor, and the module is
 * switched off for as long as you are on one of them.
 */
public final class FreelookModule extends Module {
	public final Setting.Key lookKey;
	private final Setting.Bool invert = add(new Setting.Bool("Invert Pitch", false));

	public boolean looking;
	public float yaw, pitch;

	public FreelookModule(int defaultKey) {
		super("Freelook", "Hold a key to look around without turning.");
		lookKey = add(new Setting.Key("Freelook Key", defaultKey));
		// Hypixel does not allow freelook; Lunar and Badlion turn theirs off there too.
		restrictOn("hypixel.net", "hypixel.io");
	}

	/**
	 * Call once per frame with the player's current angles.
	 *
	 * @return 1 when freelook just started, -1 when it just ended, 0 otherwise
	 */
	public int update(float playerYaw, float playerPitch) {
		boolean held = isEnabled() && lookKey.code != 0 && Onix.get().platform.isDown(lookKey.code);
		if (held == looking) return 0;
		looking = held;
		if (held) {
			yaw = playerYaw;
			pitch = playerPitch;
			return 1;
		}
		return -1;
	}

	/** Mouse movement in the units the game gives its own camera. */
	public void turn(float dx, float dy) {
		yaw += dx * 0.15f;
		pitch += (invert.on ? dy : -dy) * 0.15f;
		pitch = Math.max(-90f, Math.min(90f, pitch));
	}

	@Override
	protected void onDisable() {
		// The platform sees looking flip on its next frame and restores the view.
	}
}
