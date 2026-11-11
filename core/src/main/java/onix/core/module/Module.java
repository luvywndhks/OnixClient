package onix.core.module;

import java.util.ArrayList;
import java.util.List;

import onix.core.Onix;
import onix.core.event.Listener;
import onix.core.setting.Setting;

/**
 * A feature the player can switch on and off.
 *
 * Listeners registered with {@link #on} are attached to the event bus only
 * while the module is enabled, so a disabled module does no work at all.
 */
public abstract class Module {
	public final String name;
	public final String description;
	public final List<Setting<?>> settings = new ArrayList<Setting<?>>();
	public final Setting.Key key = new Setting.Key("Key", 0);
	public boolean favorite;
	/** False for pages that are always on, such as Global Settings. */
	public boolean toggleable = true;

	private final List<Subscription<?>> subscriptions = new ArrayList<Subscription<?>>();
	/** What the player chose. */
	private boolean wanted;
	/** Whether the module is really running: wanted, and allowed on this server. */
	private boolean enabled;
	private boolean blocked;
	/** Server host suffixes on which this module must stay off, lower case. */
	public String[] restrictedHosts = new String[0];

	protected Module(String name, String description) {
		this.name = name;
		this.description = description;
		settings.add(key);
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	protected <E> void on(Class<E> type, Listener<E> listener) {
		Subscription<E> s = new Subscription<E>(type, listener);
		subscriptions.add(s);
		if (enabled) s.attach();
	}

	public final boolean isEnabled() {
		return enabled;
	}

	/** True when the player has it switched on, even if the server blocks it. */
	public final boolean isWanted() {
		return wanted;
	}

	public final boolean isBlocked() {
		return blocked;
	}

	protected final void restrictOn(String... hosts) {
		restrictedHosts = hosts;
	}

	/** Called when the player joins or leaves a server. A blocked module is switched off, not forgotten. */
	public final void setBlocked(boolean blocked) {
		this.blocked = blocked;
		apply(wanted && !blocked);
	}

	public final void setEnabled(boolean wanted) {
		this.wanted = wanted;
		apply(wanted && !blocked);
	}

	private void apply(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) {
			for (Subscription<?> s : subscriptions) s.attach();
			onEnable();
		} else {
			for (Subscription<?> s : subscriptions) s.detach();
			onDisable();
		}
	}

	public final void toggle() {
		setEnabled(!wanted);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public Setting<?> setting(String name) {
		for (Setting<?> s : settings) {
			if (s.name.equals(name)) return s;
		}
		return null;
	}

	private static final class Subscription<E> {
		final Class<E> type;
		final Listener<E> listener;

		Subscription(Class<E> type, Listener<E> listener) {
			this.type = type;
			this.listener = listener;
		}

		void attach() {
			Onix.get().events.subscribe(type, listener);
		}

		void detach() {
			Onix.get().events.unsubscribe(type, listener);
		}
	}
}
