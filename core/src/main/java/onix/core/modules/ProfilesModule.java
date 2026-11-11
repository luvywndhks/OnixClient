package onix.core.modules;

import onix.core.module.Module;

/**
 * The Profiles page of the menu, listed right under Global Settings. It has
 * no options of its own: the menu draws the profile cards in place of the
 * usual rows, and the profiles themselves are handled by the config manager.
 */
public final class ProfilesModule extends Module {
	public ProfilesModule() {
		super("Profiles", "Save your settings as profiles and switch between them.");
		toggleable = false;
		settings.remove(key);
	}
}
