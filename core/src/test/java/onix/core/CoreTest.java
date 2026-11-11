package onix.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import onix.core.event.Events;
import onix.core.module.HudModule;
import onix.core.module.Module;
import onix.core.platform.Platform;
import onix.core.render.Renderer;
import onix.core.setting.Setting;

public class CoreTest {
	static final class FakePlatform implements Platform {
		final File dir;
		final Set<Integer> down = new HashSet<Integer>();
		boolean sprint;
		float gamma = 0.5f;

		FakePlatform(File dir) {
			this.dir = dir;
		}

		public File configDir() { return dir; }
		public int fps() { return 144; }
		public boolean inWorld() { return true; }
		public double playerX() { return 10.5; }
		public double playerY() { return 64; }
		public double playerZ() { return -3.2; }
		public boolean isDown(int code) { return down.contains(code); }
		public String keyName(int code) { return "K" + code; }
		public int[] controlKeys() { return new int[] { 17, 30, 31, 32, 57, 42, 29, -100, -99 }; }
		public void setSprinting(boolean held) { sprint = held; }
		public float gamma() { return gamma; }
		public void setGamma(float g) { gamma = g; }
		public int defaultMenuKey() { return 54; }
		public int ping() { return 42; }
		public String serverAddress() { return server; }
		public float yaw() { return 90f; }
		public long timeOfDay() { return 48000L; }
		public float health() { return 20f; }
		public float maxHealth() { return 20f; }
		public void setHitboxes(boolean shown) { hitboxes = shown; }
		public boolean windowFocused() { return focused; }
		public int fpsLimit() { return limit; }
		public void setFpsLimit(int l) { limit = l; }
		public int defaultZoomKey() { return 46; }
		public void openFolder(File folder) { opened = folder; }
		File opened;
		public int defaultFreelookKey() { return 56; }
		public int countItems(int kind) { return kind == ARROWS ? 12 : 3; }
		public float saturation() { return 5.2f; }
		public void setSneaking(boolean held) { }
		public void sendChat(String message) { sent.add(message); }
		public String username() { return "Steve"; }
		final List<String> sent = new ArrayList<String>();
		public boolean crosshairWanted() { return true; }
		public boolean lookingAtEntity() { return false; }
		public void setTimeOfDay(long time) { clientTime = time; }
		public void setWeather(float rain, float thunder) { }
		long clientTime = -1;
		public int itemDurability(int slot) { return slot == 0 ? 100 : NO_ITEM; }
		public int itemMaxDurability(int slot) { return 165; }
		public int itemCount(int slot) { return slot == INVENTORY + 9 ? 64 : slot < INVENTORY ? 1 : 0; }
		public void effects(java.util.List<String> lines, java.util.List<Integer> ticks) { lines.clear(); ticks.clear(); lines.add("Speed II 1:30"); ticks.add(1800); }
		String server = "example.net";
		boolean hitboxes, focused = true;
		int limit = 240;
	}

	/** Records what was drawn so tests can look for it. */
	static final class FakeRenderer implements Renderer {
		final List<String> texts = new ArrayList<String>();
		final List<float[]> textAt = new ArrayList<float[]>();
		int panels, depth;

		public void panel(float x, float y, float w, float h, float r, int fill, float bw, int bc, boolean blur) { panels++; }
		public void glow(float x, float y, float w, float h, float r, float spread, int color) { }
		public void item(int slot, float x, float y, float size) { items++; }
		public void topLayer(boolean on) { }
		public void logo(float x, float y, float size) { }
		public void font(int font) { }
		int items;
		public void text(String text, float x, float y, float size, int color, int weight) {
			texts.add(text);
			textAt.add(new float[] { x, y });
		}
		public float textWidth(String text, float size, int weight) { return text.length() * size * 0.5f; }
		public float lineHeight(float size) { return size * 1.4f; }
		public void pushClip(float x, float y, float w, float h) { depth++; }
		public void popClip() { depth--; }
		public void pushScale(float f, float ox, float oy) { depth++; }
		public void popScale() { depth--; }
		public void pushAlpha(float alpha) { depth++; }
		public void popAlpha() { depth--; }

		float[] find(String text) {
			int i = texts.indexOf(text);
			return i < 0 ? null : textAt.get(i);
		}
	}

	static FakePlatform platform;
	static Onix onix;

	@BeforeClass
	public static void setUp() throws Exception {
		platform = new FakePlatform(Files.createTempDirectory("onix-test").toFile());
		onix = Onix.init(platform);
	}

	private static FakeRenderer frame(float mx, float my) {
		FakeRenderer r = new FakeRenderer();
		onix.gui.render(r, 1920, 1080, mx, my, 1f);
		assertEquals("clip and alpha stacks must balance", 0, r.depth);
		return r;
	}

	@Test
	public void disabledModuleHasNoListeners() {
		Module fps = onix.module("FPS Counter");
		fps.setEnabled(false);
		int before = onix.events.count(Events.HudRender.class);
		fps.setEnabled(true);
		assertEquals(before + 1, onix.events.count(Events.HudRender.class));
		fps.setEnabled(false);
		assertEquals(before, onix.events.count(Events.HudRender.class));
	}

	@Test
	public void hudDrawsOnlyWhenEnabled() {
		Module fps = onix.module("FPS Counter");
		fps.setEnabled(true);
		FakeRenderer r = new FakeRenderer();
		onix.renderHud(r, 1920, 1080, 0.016f);
		assertTrue(r.texts.contains("144 FPS"));
		HudModule hud = (HudModule) fps;
		assertTrue("box stays on screen", hud.lastX >= 0 && hud.lastX + hud.lastW <= 1920);

		fps.setEnabled(false);
		r = new FakeRenderer();
		onix.renderHud(r, 1920, 1080, 0.016f);
		assertFalse(r.texts.contains("144 FPS"));
	}

	@Test
	public void menuListsModulesAndClickSelectsAndToggles() {
		onix.gui.open();
		onix.gui.showMenu();
		for (int i = 0; i < 12; i++) frame(0, 0);
		FakeRenderer r = frame(0, 0);
		assertTrue(r.texts.contains("Search..."));
		assertTrue(r.texts.contains("Global Settings"));
		float[] at = r.find("Clock");
		assertNotNull("module row is drawn", at);

		// Click the row text: selects the module, so its page shows.
		onix.gui.mouseDown(at[0] + 5, at[1] + 5, 0);
		onix.gui.mouseUp(0);
		frame(at[0] + 5, at[1] + 5);
		r = frame(at[0] + 5, at[1] + 5);
		assertTrue(r.texts.contains("Shows the time of your computer."));
		assertTrue(r.texts.contains("Disabled"));

		// Right click on the row toggles it.
		Module keys = onix.module("Clock");
		assertFalse(keys.isEnabled());
		onix.gui.mouseDown(at[0] + 5, at[1] + 5, 1);
		frame(at[0] + 5, at[1] + 5);
		assertTrue(keys.isEnabled());
		keys.setEnabled(false);
	}

	@Test
	public void searchFilters() {
		onix.gui.open();
		onix.gui.showMenu();
		for (int i = 0; i < 12; i++) frame(0, 0);
		for (char c : "fps".toCharArray()) onix.gui.keyDown(0, c, false, false);
		FakeRenderer r = frame(0, 0);
		assertTrue(r.texts.contains("FPS Counter"));
		assertFalse(r.texts.contains("Coordinates"));
		for (int i = 0; i < 3; i++) onix.gui.keyDown(0, (char) 0, false, true);
		r = frame(0, 0);
		assertTrue(r.texts.contains("Coordinates"));
	}

	@Test
	public void escapeClosesAfterAnimation() {
		onix.gui.open();
		onix.gui.showMenu();
		for (int i = 0; i < 12; i++) frame(0, 0);
		frame(0, 0);
		assertFalse(onix.gui.isClosed());
		onix.gui.keyDown(1, (char) 0, true, false);
		for (int i = 0; i < 15; i++) frame(0, 0);
		assertTrue(onix.gui.isClosed());
	}

	@Test
	public void configRoundTrip() {
		Module item = onix.module("Item Size");
		Setting.Number size = (Setting.Number) item.setting("Size");
		size.set(1.5f);
		item.setEnabled(true);
		item.key.set(33);
		onix.config.save();

		size.set(0.3f);
		item.setEnabled(false);
		item.key.set(0);
		onix.config.load();
		assertEquals(1.5f, size.v, 0.0001f);
		assertTrue(item.isEnabled());
		assertEquals(33, item.key.code);
		item.setEnabled(false);
		item.key.set(0);
	}

	@Test
	public void numberSettingClampsAndSnaps() {
		Setting.Number n = new Setting.Number("n", 1f, 0f, 2f, 0.25f);
		n.set(5f);
		assertEquals(2f, n.v, 0f);
		n.set(0.6f);
		assertEquals(0.5f, n.v, 0.0001f);
	}

	@Test
	public void keybindTogglesOnPressOnly() {
		Module full = onix.module("Fullbright");
		full.key.set(50);
		onix.pollInput();
		platform.down.add(50);
		onix.pollInput();
		assertTrue(full.isEnabled());
		assertEquals(25f, platform.gamma, 0f);
		onix.pollInput();
		assertTrue("holding must not toggle again", full.isEnabled());
		platform.down.remove(50);
		onix.pollInput();
		platform.down.add(50);
		onix.pollInput();
		assertFalse(full.isEnabled());
		assertEquals("gamma is restored", 0.5f, platform.gamma, 0f);
		platform.down.remove(50);
		full.key.set(0);
	}

	@Test
	public void toggleSprintHoldsAndReleases() {
		Module sprint = onix.module("Toggle Sprint/Sneak");
		sprint.setEnabled(true);
		onix.tick();
		assertTrue(platform.sprint);
		sprint.setEnabled(false);
		assertFalse(platform.sprint);
	}

	@Test
	public void restrictedModuleIsBlockedOnlyOnThatServer() {
		Module freelook = onix.module("Freelook");
		freelook.setEnabled(true);
		onix.tick();
		assertTrue(freelook.isEnabled());

		platform.server = "mc.hypixel.net:25565";
		onix.tick();
		assertTrue(freelook.isBlocked());
		assertFalse("blocked modules do not run", freelook.isEnabled());
		assertTrue("the choice is remembered", freelook.isWanted());
		assertTrue("other modules are untouched", !onix.module("Zoom").isBlocked());

		platform.server = "example.net";
		onix.tick();
		assertFalse(freelook.isBlocked());
		assertTrue("it comes back on after leaving", freelook.isEnabled());
		freelook.setEnabled(false);
	}

	@Test
	public void comboCountsHitsAndResetsWhenHurt() {
		Module combo = onix.module("Combo Counter");
		combo.setEnabled(true);
		onix.events.post(Events.HitLanded.INSTANCE);
		onix.events.post(Events.HitLanded.INSTANCE);
		FakeRenderer r = new FakeRenderer();
		onix.renderHud(r, 1920, 1080, 0.016f);
		assertTrue(r.texts.contains("2 Combo"));
		onix.events.post(Events.PlayerHurt.INSTANCE);
		r = new FakeRenderer();
		onix.renderHud(r, 1920, 1080, 0.016f);
		assertTrue(r.texts.contains("No Combo"));
		combo.setEnabled(false);
	}

	@Test
	public void armorHudDrawsOnlyFilledSlots() {
		Module armor = onix.module("Armor HUD");
		armor.setEnabled(true);
		FakeRenderer r = new FakeRenderer();
		onix.renderHud(r, 1920, 1080, 0.016f);
		assertEquals(1, r.items);
		assertTrue(r.texts.contains("100"));
		armor.setEnabled(false);
	}

	@Test
	public void autoGgSendsOncePerGameAndIgnoresPlayers() {
		Module gg = onix.module("AutoGG");
		((Setting.Number) gg.setting("Delay (seconds)")).set(0f);
		gg.setEnabled(true);
		platform.sent.clear();

		Events.Chat.INSTANCE.text = "[MVP] Someone: 1st Killer - me lol";
		onix.events.post(Events.Chat.INSTANCE);
		onix.tick();
		assertTrue("a player typing a trigger must not fire it", platform.sent.isEmpty());

		Events.Chat.INSTANCE.text = "                  1st Killer - Steve - 7";
		onix.events.post(Events.Chat.INSTANCE);
		onix.tick();
		assertEquals(1, platform.sent.size());
		assertEquals("gg", platform.sent.get(0));

		onix.events.post(Events.Chat.INSTANCE);
		onix.tick();
		assertEquals("only one gg per game", 1, platform.sent.size());
		gg.setEnabled(false);
	}

	@Test
	public void nickHiderReplacesOnlyTheOwnName() {
		onix.core.modules.ConvenienceModules.NickHider hider = onix.nickHider;
		hider.setEnabled(true);
		assertEquals("<You> hello", hider.apply("<Steve> hello"));
		assertEquals("<Alex> hello", hider.apply("<Alex> hello"));
		hider.setEnabled(false);
	}

	@Test
	public void profilesKeepSeparateSettings() {
		Setting.Number size = (Setting.Number) onix.module("Item Size").setting("Size");
		Module clock = onix.module("Clock");
		String first = onix.config.active();
		size.set(1.75f);
		clock.setEnabled(true);

		String second = onix.config.freeName("Test Profile");
		assertTrue(onix.config.create(second));
		assertTrue(onix.config.use(second));
		assertEquals("a new profile starts as a copy", 1.75f, size.v, 0.0001f);
		size.set(0.5f);
		clock.setEnabled(false);

		assertTrue(onix.config.use(first));
		assertEquals("the first profile is untouched", 1.75f, size.v, 0.0001f);
		assertTrue(clock.isEnabled());
		assertTrue(onix.config.use(second));
		assertEquals(0.5f, size.v, 0.0001f);
		assertFalse(clock.isEnabled());

		String copy = onix.config.duplicate(second);
		assertNotNull(copy);
		assertTrue(onix.config.profiles().contains(copy));
		assertTrue(onix.config.rename(copy, "Renamed One"));
		assertFalse("names with path characters are refused", onix.config.rename("Renamed One", "../evil"));
		assertFalse("the profile in use cannot be deleted", onix.config.delete(second));
		assertTrue(onix.config.delete("Renamed One"));

		assertTrue(onix.config.use(first));
		assertTrue(onix.config.delete(second));
		size.reset();
		clock.setEnabled(false);
	}
}
