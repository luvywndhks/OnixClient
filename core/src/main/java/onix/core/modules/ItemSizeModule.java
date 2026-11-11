package onix.core.modules;

import onix.core.module.Module;
import onix.core.setting.Setting;

/**
 * Resizes and moves the item in your hand. The values are read by the
 * held-item hook of each platform; this class only stores them.
 */
public final class ItemSizeModule extends Module {
	public final Setting.Number size = add(new Setting.Number("Size", 1f, 0.1f, 2f, 0.05f));
	public final Setting.Number x = add(new Setting.Number("X Offset", 0f, -1f, 1f, 0.01f).section("Position"));
	public final Setting.Number y = add(new Setting.Number("Y Offset", 0f, -1f, 1f, 0.01f).section("Position"));
	public final Setting.Number z = add(new Setting.Number("Z Offset", 0f, -1f, 1f, 0.01f).section("Position"));

	public ItemSizeModule() {
		super("Item Size", "Changes the size and position of the item in your hand.");
		add(new Setting.Action("Reset to Default", "Reset", new Runnable() {
			@Override
			public void run() {
				size.reset();
				x.reset();
				y.reset();
				z.reset();
			}
		}));
	}
}
