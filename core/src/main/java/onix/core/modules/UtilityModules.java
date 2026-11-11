package onix.core.modules;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.module.Module;
import onix.core.platform.Platform;
import onix.core.setting.Setting;

public final class UtilityModules {
	private UtilityModules() {
	}

	/** Shows entity hitboxes, the same boxes as F3+B. */
	public static final class Hitboxes extends Module {
		public Hitboxes() {
			super("Hitboxes", "Shows the hitboxes of entities.");
		}

		@Override
		protected void onEnable() {
			Onix.get().platform.setHitboxes(true);
		}

		@Override
		protected void onDisable() {
			Onix.get().platform.setHitboxes(false);
		}
	}

	/** Tints the screen red, pulsing, when health drops under a threshold. */
	public static final class LowHealthWarning extends Module {
		private final Setting.Number threshold = add(new Setting.Number("Health Threshold", 6f, 1f, 20f, 1f)
				.note("In half hearts. 6 means three hearts."));
		private final Setting.Number strength = add(new Setting.Number("Strength", 0.25f, 0.05f, 0.6f, 0.05f));
		private final Setting.Bool pulse = add(new Setting.Bool("Pulse", true));

		public LowHealthWarning() {
			super("Low Health Warning", "Warns you when your health is low.");
			on(Events.HudRender.class, new Listener<Events.HudRender>() {
				@Override
				public void on(Events.HudRender e) {
					Platform p = Onix.get().platform;
					if (!p.inWorld()) return;
					float health = p.health();
					if (health <= 0f || health > threshold.v) return;
					// Redder the closer to death.
					float danger = 1f - health / Math.max(1f, threshold.v) * 0.6f;
					float wave = pulse.on ? 0.65f + 0.35f * (float) Math.sin(System.nanoTime() / 1e9 * 5.0) : 1f;
					int alpha = Math.round(255f * strength.v * danger * wave);
					e.renderer.panel(0f, 0f, e.width, e.height, 0f, (alpha << 24) | 0xD01818, 0f, 0, false);
				}
			});
		}
	}

}
