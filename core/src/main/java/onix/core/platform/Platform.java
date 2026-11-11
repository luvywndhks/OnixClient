package onix.core.platform;

import java.io.File;

/**
 * The only door between the core and Minecraft. Each supported game version
 * implements this once; modules never touch game classes directly.
 */
public interface Platform {
	File configDir();

	int fps();

	boolean inWorld();

	double playerX();

	double playerY();

	double playerZ();

	/** Key codes are the platform's own. Mouse buttons are -100 + button. */
	boolean isDown(int code);

	String keyName(int code);

	/** Forward, left, back, right, jump, sneak, sprint, attack, use. */
	int[] controlKeys();

	/** Holds or releases the sprint key for the player. */
	void setSprinting(boolean held);

	void setSneaking(boolean held);

	void sendChat(String message);

	/** The name of the logged in player. */
	String username();

	float gamma();

	void setGamma(float gamma);

	/** Latency to the server in milliseconds, or -1 when unknown (single player). */
	int ping();

	/** Address of the current server, or null in single player. */
	String serverAddress();

	/** Player yaw in degrees; 0 faces south, as in the game. */
	float yaw();

	long timeOfDay();

	float health();

	float maxHealth();

	void setHitboxes(boolean shown);

	boolean windowFocused();

	int fpsLimit();

	void setFpsLimit(int limit);

	int defaultZoomKey();

	/** Shows a folder in the file manager of the operating system. */
	void openFolder(java.io.File folder);

	int defaultFreelookKey();

	int NO_ITEM = -1, UNBREAKABLE = -2;

	/** Item slots from this number up are inventory slots: INVENTORY + 0..8 hotbar, + 9..35 backpack. */
	int INVENTORY = 100;

	int ARROWS = 0, HEAL_POTIONS = 1, SOUPS = 2;

	/** How many of a kind of item the player carries. */
	int countItems(int kind);

	float saturation();

	/** True in the frames where the game itself would draw a crosshair. */
	boolean crosshairWanted();

	boolean lookingAtEntity();

	/** Client side only; the server is not told. */
	void setTimeOfDay(long time);

	/** Client side only. Both values run from 0 (none) to 1 (full). */
	void setWeather(float rain, float thunder);

	/**
	 * Uses left on the item in a slot (0 to 3 helmet to boots, 4 held), {@link #NO_ITEM}
	 * for an empty slot, or {@link #UNBREAKABLE} for an item that does not wear out.
	 */
	int itemDurability(int slot);

	int itemMaxDurability(int slot);

	int itemCount(int slot);

	/** Fills the lists with one "Name II 1:30" line and the ticks left for each active effect. */
	void effects(java.util.List<String> lines, java.util.List<Integer> ticksLeft);

	/** The key that opens the client menu when the config has none. */
	int defaultMenuKey();
}
