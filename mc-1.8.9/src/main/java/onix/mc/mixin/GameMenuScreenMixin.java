package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

import onix.mc.Bridge;
import onix.mc.OnixScreen;
import onix.mc.ThemedButton;
import onix.mc.VanillaTheme;

/**
 * The pause menu: the wordmark above the buttons, lower rows, and a Mods
 * button that opens the client menu directly.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
	private static final int MODS = 9100;
	private static final int ROW = 18;

	@Inject(method = "init", at = @At("TAIL"))
	private void onix$layout(CallbackInfo ci) {
		int lowest = 0;
		for (int i = 0; i < buttons.size(); i++) {
			ButtonWidget b = buttons.get(i);
			((ThemedButton) b).onix$bounds(b.x, b.y, b.getWidth(), ROW);
			lowest = Math.max(lowest, b.y);
		}
		buttons.add(new ButtonWidget(MODS, width / 2 - 100, lowest + 24, 200, ROW, "Mods"));
	}

	@Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
	private void onix$mods(ButtonWidget button, CallbackInfo ci) {
		if (button.id != MODS) return;
		minecraft.openScreen(new OnixScreen());
		// Straight to the module list, skipping the HUD editor.
		Bridge.get().onix.gui.showMenu();
		ci.cancel();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void onix$wordmark(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		int top = height;
		for (int i = 0; i < buttons.size(); i++) top = Math.min(top, buttons.get(i).y);
		VanillaTheme.wordmark(top - 10, 20f);
	}
}
