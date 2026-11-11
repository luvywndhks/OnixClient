package onix.modern;

import java.io.File;
import java.util.List;


import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import onix.core.Onix;
import onix.core.platform.Platform;

/**
 * Modern Minecraft behind the core's {@link Platform} interface.
 *
 * Key codes are the game's own; mouse buttons are -100 + button, as in the core.
 * A few things the 1.8.9 adapter does are not connected here yet and answer
 * with a harmless default; each is marked.
 */
public final class ModernPlatform implements Platform {
	private final Minecraft mc = Minecraft.getInstance();
	private final int[] controls = new int[9];

	@Override
	public File configDir() {
		return new File(mc.gameDirectory, Onix.ID);
	}

	@Override
	public int fps() {
		return mc.getFps();
	}

	@Override
	public boolean inWorld() {
		return mc.player != null && mc.level != null;
	}

	@Override
	public double playerX() {
		return mc.player == null ? 0 : mc.player.getX();
	}

	@Override
	public double playerY() {
		return mc.player == null ? 0 : mc.player.getY();
	}

	@Override
	public double playerZ() {
		return mc.player == null ? 0 : mc.player.getZ();
	}

	@Override
	public boolean isDown(int code) {
		if (code == 0) return false;
		if (code < 0) {
			// The game tracks the three main buttons; others are not readable this way yet.
			int button = code + 100;
			return button == 0 ? mc.mouseHandler.isLeftPressed() : button == 1 ? mc.mouseHandler.isRightPressed()
					: button == 2 && mc.mouseHandler.isMiddlePressed();
		}
		return InputConstants.isKeyDown(code);
	}

	@Override
	public String keyName(int code) {
		if (code < 0) {
			int button = code + 100;
			return button == 0 ? "LMB" : button == 1 ? "RMB" : button == 2 ? "MMB" : "Mouse " + (button + 1);
		}
		// The game's own name for the key, in the player's language and keyboard layout.
		return InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
	}

	private static int code(KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		return key.getType() == InputConstants.Type.MOUSE ? key.getValue() - 100 : key.getValue();
	}

	@Override
	public int[] controlKeys() {
		controls[0] = code(mc.options.keyUp);
		controls[1] = code(mc.options.keyLeft);
		controls[2] = code(mc.options.keyDown);
		controls[3] = code(mc.options.keyRight);
		controls[4] = code(mc.options.keyJump);
		controls[5] = code(mc.options.keyShift);
		controls[6] = code(mc.options.keySprint);
		controls[7] = code(mc.options.keyAttack);
		controls[8] = code(mc.options.keyUse);
		return controls;
	}

	@Override
	public void setSprinting(boolean held) {
		// On release, fall back to whether the player is really holding the key.
		mc.options.keySprint.setDown(held || isDown(code(mc.options.keySprint)));
	}

	@Override
	public void setSneaking(boolean held) {
		mc.options.keyShift.setDown(held || isDown(code(mc.options.keyShift)));
	}

	@Override
	public void sendChat(String message) {
		if (mc.getConnection() != null) mc.getConnection().sendChat(message);
	}

	@Override
	public String username() {
		return mc.getUser().getName();
	}

	@Override
	public float gamma() {
		return mc.options.gamma().get().floatValue();
	}

	@Override
	public void setGamma(float gamma) {
		// The modern option only accepts 0 to 1, so Fullbright cannot go past full brightness here yet.
		mc.options.gamma().set((double) Math.max(0f, Math.min(1f, gamma)));
	}

	@Override
	public int ping() {
		if (mc.player == null || mc.getConnection() == null) return -1;
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	@Override
	public String serverAddress() {
		ServerData server = mc.getCurrentServer();
		return server == null ? null : server.ip;
	}

	@Override
	public float yaw() {
		return mc.player == null ? 0f : mc.player.getYRot();
	}

	@Override
	public long timeOfDay() {
		return mc.level == null ? 0L : mc.level.getOverworldClockTime();
	}

	@Override
	public float health() {
		return mc.player == null ? 0f : mc.player.getHealth();
	}

	@Override
	public float maxHealth() {
		return mc.player == null ? 20f : mc.player.getMaxHealth();
	}

	@Override
	public void setHitboxes(boolean shown) {
		// Not connected on modern versions yet.
	}

	@Override
	public boolean windowFocused() {
		return mc.isWindowActive();
	}

	@Override
	public int fpsLimit() {
		return mc.options.framerateLimit().get();
	}

	@Override
	public void setFpsLimit(int limit) {
		mc.options.framerateLimit().set(Math.max(10, limit));
	}

	@Override
	public int defaultZoomKey() {
		return InputConstants.KEY_C;
	}

	@Override
	public void openFolder(File folder) {
		try {
			if (!folder.isDirectory() && !folder.mkdirs()) return;
			if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
				new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
			} else {
				java.awt.Desktop.getDesktop().open(folder);
			}
		} catch (Exception e) {
			System.err.println("[Onix] Could not open " + folder + ": " + e);
		}
	}

	@Override
	public int defaultFreelookKey() {
		return InputConstants.KEY_LALT;
	}

	@Override
	public int countItems(int kind) {
		// Not connected on modern versions yet.
		return 0;
	}

	@Override
	public float saturation() {
		return mc.player == null ? 0f : mc.player.getFoodData().getSaturationLevel();
	}

	@Override
	public boolean crosshairWanted() {
		// Not connected on modern versions yet, so Custom Crosshair draws nothing.
		return false;
	}

	@Override
	public boolean lookingAtEntity() {
		return false;
	}

	@Override
	public void setTimeOfDay(long time) {
		// Not connected on modern versions yet.
	}

	@Override
	public void setWeather(float rain, float thunder) {
		// Not connected on modern versions yet.
	}

	@Override
	public int itemDurability(int slot) {
		// Not connected on modern versions yet, so item HUDs stay hidden.
		return NO_ITEM;
	}

	@Override
	public int itemMaxDurability(int slot) {
		return 0;
	}

	@Override
	public int itemCount(int slot) {
		return 0;
	}

	@Override
	public void effects(List<String> lines, List<Integer> ticksLeft) {
		// Not connected on modern versions yet.
		lines.clear();
		ticksLeft.clear();
	}

	@Override
	public int defaultMenuKey() {
		return InputConstants.KEY_RSHIFT;
	}
}
