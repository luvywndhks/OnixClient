package onix.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import onix.core.config.ConfigManager;
import onix.core.event.EventBus;
import onix.core.event.Events;
import onix.core.input.ClickTracker;
import onix.core.module.Module;
import onix.core.modules.AnimationModules;
import onix.core.modules.CombatHudModules;
import onix.core.modules.ConvenienceModules;
import onix.core.modules.CoordinatesModule;
import onix.core.modules.ExtraModules;
import onix.core.modules.FreelookModule;
import onix.core.modules.CpsModule;
import onix.core.modules.FpsModule;
import onix.core.modules.FullbrightModule;
import onix.core.modules.GlobalSettings;
import onix.core.modules.ItemSizeModule;
import onix.core.modules.KeystrokesModule;
import onix.core.modules.MotionBlurModule;
import onix.core.modules.ProfilesModule;
import onix.core.modules.PvpModules;
import onix.core.modules.InfoModules;
import onix.core.modules.InventoryHudModules;
import onix.core.modules.ToggleSprintModule;
import onix.core.modules.UtilityModules;
import onix.core.modules.ZoomModule;
import onix.core.platform.Platform;
import onix.core.theme.Theme;
import onix.core.ui.ClickGui;

/** The client itself: owns the modules, the event bus and the menu. */
public final class Onix {
	/** The one place the brand is spelled out. */
	public static final String NAME = "Onix Client";
	public static final String ID = "onix";

	private static Onix instance;

	public final Platform platform;
	public final EventBus events = new EventBus();
	public final Theme theme = new Theme();
	public final ClickTracker clicks = new ClickTracker();
	public final List<Module> modules = new ArrayList<Module>();
	public final ConfigManager config;
	public final ClickGui gui;

	public final GlobalSettings global;
	public final ProfilesModule profiles;
	public final ItemSizeModule itemSize;
	public final ZoomModule zoom;
	public final FreelookModule freelook;
	public final MotionBlurModule motionBlur;
	public final ExtraModules.Chat chat;
	public final ExtraModules.GlintColor glintColor;
	public final ExtraModules.NumericalEnchants numericalEnchants;
	public final ExtraModules.ParticleMultiplier particleMultiplier;
	public final ExtraModules.OwnNametag ownNametag;
	public final ExtraModules.Perspective perspective;
	public final ConvenienceModules.NickHider nickHider;
	public final PvpModules.Crosshair crosshair;
	public final PvpModules.HurtColor hurtColor;
	public final PvpModules.BlockOutline blockOutline;
	public final AnimationModules.OldAnimations oldAnimations;
	public final AnimationModules.LunarAnimation lunarAnimation;

	private boolean[] wasDown = new boolean[0];
	private boolean menuWasDown;

	private Onix(Platform platform) {
		this.platform = platform;
		instance = this;

		global = register(new GlobalSettings(platform.defaultMenuKey()));
		profiles = register(new ProfilesModule());
		itemSize = register(new ItemSizeModule());
		register(new FpsModule());
		register(new CpsModule());
		register(new KeystrokesModule());
		register(new CoordinatesModule());
		register(new ToggleSprintModule());
		register(new FullbrightModule());
		zoom = register(new ZoomModule(platform.defaultZoomKey()));
		oldAnimations = register(new AnimationModules.OldAnimations());
		lunarAnimation = register(new AnimationModules.LunarAnimation());
		freelook = register(new FreelookModule(platform.defaultFreelookKey()));
		register(new CombatHudModules.Armor());
		register(new CombatHudModules.Potions());
		register(new CombatHudModules.Combo());
		register(new CombatHudModules.Reach());
		register(new InventoryHudModules.Inventory());
		register(new InventoryHudModules.Saturation());
		register(new InventoryHudModules.ArrowCounter());
		register(new InventoryHudModules.PotionCounter());
		register(new InventoryHudModules.Tps());
		register(new InventoryHudModules.Playtime());
		crosshair = register(new PvpModules.Crosshair());
		hurtColor = register(new PvpModules.HurtColor());
		blockOutline = register(new PvpModules.BlockOutline());
		register(new PvpModules.TimeChanger());
		register(new PvpModules.WeatherChanger());
		register(new ConvenienceModules.AutoGG());
		nickHider = register(new ConvenienceModules.NickHider());
		motionBlur = register(new MotionBlurModule());
		chat = register(new ExtraModules.Chat());
		glintColor = register(new ExtraModules.GlintColor());
		numericalEnchants = register(new ExtraModules.NumericalEnchants());
		particleMultiplier = register(new ExtraModules.ParticleMultiplier());
		ownNametag = register(new ExtraModules.OwnNametag());
		perspective = register(new ExtraModules.Perspective());
		register(new InfoModules.Clock());
		register(new InfoModules.Ping());
		register(new InfoModules.ServerIp());
		register(new InfoModules.Speed());
		register(new InfoModules.Direction());
		register(new InfoModules.DayCounter());
		register(new InfoModules.Memory());
		register(new UtilityModules.Hitboxes());
		register(new UtilityModules.LowHealthWarning());

		Collections.sort(modules, new Comparator<Module>() {
			@Override
			public int compare(Module a, Module b) {
				if (a.toggleable != b.toggleable) return a.toggleable ? 1 : -1;
				return a.name.compareToIgnoreCase(b.name);
			}
		});

		config = new ConfigManager(this, platform.configDir());
		config.load();
		// Global Settings has no switch; it is simply always running.
		global.setEnabled(true);
		gui = new ClickGui(this);
	}

	public static Onix init(Platform platform) {
		if (instance == null) new Onix(platform);
		return instance;
	}

	public static Onix get() {
		return instance;
	}

	private <M extends Module> M register(M module) {
		modules.add(module);
		return module;
	}

	public Module module(String name) {
		for (Module m : modules) {
			if (m.name.equals(name)) return m;
		}
		return null;
	}

	private String lastServer = "0000";

	/** Switches off, for this server only, the modules it does not allow. */
	private void applyRestrictions() {
		String address = platform.serverAddress();
		String key = address == null ? "" : address;
		if (key.equals(lastServer)) return;
		lastServer = key;
		String host = key.toLowerCase(java.util.Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0) host = host.substring(0, colon);
		for (Module m : modules) {
			boolean blocked = false;
			for (String suffix : m.restrictedHosts) {
				if (host.equals(suffix) || host.endsWith("." + suffix)) blocked = true;
			}
			if (blocked != m.isBlocked()) m.setBlocked(blocked);
		}
	}

	public void tick() {
		applyRestrictions();
		events.post(Events.Tick.INSTANCE);
	}

	/**
	 * Call once per frame while the player is in game with no screen open.
	 * Finds key and mouse edges for module binds, clicks and the menu key.
	 *
	 * @return true when the menu key was just pressed
	 */
	public boolean pollInput() {
		int n = modules.size();
		if (wasDown.length != n) wasDown = new boolean[n];
		for (int i = 0; i < n; i++) {
			Module m = modules.get(i);
			int code = m.key.code;
			boolean down = code != 0 && platform.isDown(code);
			if (down && !wasDown[i] && m.toggleable) m.toggle();
			wasDown[i] = down;
		}
		clicks.poll(platform);

		int menu = global.menuKey.code;
		boolean menuDown = menu != 0 && platform.isDown(menu);
		boolean pressed = menuDown && !menuWasDown;
		menuWasDown = menuDown;
		return pressed;
	}

	/** Call when a screen opens, so keys held across it do not fire on return. */
	public void resetInput() {
		for (int i = 0; i < wasDown.length; i++) wasDown[i] = true;
		menuWasDown = true;
	}

	public void renderHud(onix.core.render.Renderer renderer, float width, float height, float delta) {
		Events.HudRender e = Events.HudRender.INSTANCE;
		e.renderer = renderer;
		e.width = width;
		e.height = height;
		e.delta = delta;
		// HUD modules draw in the font picked in Global Settings; everything else keeps the client font.
		renderer.font(global.hudFont.index);
		events.post(e);
		renderer.font(onix.core.render.Renderer.FONT_CLIENT);
	}
}
