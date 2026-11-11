package onix.core.modules;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.platform.Platform;
import onix.core.setting.Setting;

/**
 * Keeps sprint held, and optionally turns the sneak key into a switch: press
 * once to sneak, press again to stand. The HUD line says which are active.
 */
public final class ToggleSprintModule extends TextHudModule {
	private final Setting.Bool sprint = add(new Setting.Bool("Toggle Sprint", true));
	private final Setting.Bool sneak = add(new Setting.Bool("Toggle Sneak", false));
	private final Setting.Bool showText = add(new Setting.Bool("Show Text", true));

	private boolean sneaking, sneakKeyWasDown, sprintApplied, sneakApplied;

	public ToggleSprintModule() {
		super("Toggle Sprint/Sneak", "Sprint and sneak without holding the key.", 0.04f, 0.975f);
		on(Events.Tick.class, new Listener<Events.Tick>() {
			@Override
			public void on(Events.Tick e) {
				Platform p = Onix.get().platform;
				if (sprint.on) {
					p.setSprinting(true);
					sprintApplied = true;
				} else if (sprintApplied) {
					p.setSprinting(false);
					sprintApplied = false;
				}

				boolean down = p.isDown(p.controlKeys()[5]);
				if (sneak.on) {
					if (down && !sneakKeyWasDown) sneaking = !sneaking;
					p.setSneaking(sneaking);
					sneakApplied = true;
				} else if (sneakApplied) {
					sneaking = false;
					p.setSneaking(false);
					sneakApplied = false;
				}
				sneakKeyWasDown = down;
			}
		});
	}

	@Override
	protected void onDisable() {
		Platform p = Onix.get().platform;
		if (sprintApplied) p.setSprinting(false);
		if (sneakApplied) p.setSneaking(false);
		sprintApplied = false;
		sneakApplied = false;
		sneaking = false;
	}

	@Override
	protected boolean visible() {
		return showText.on && Onix.get().platform.inWorld() && (sprint.on || sneaking);
	}

	@Override
	protected long valueKey() {
		return (sprint.on ? 1L : 0L) | (sneaking ? 2L : 0L);
	}

	@Override
	protected String format() {
		if (sneaking) return "Sneaking: (Toggled)";
		return "Sprinting: (Toggled)";
	}
}
