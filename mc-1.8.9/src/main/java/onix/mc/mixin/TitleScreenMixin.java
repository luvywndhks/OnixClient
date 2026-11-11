package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

import onix.mc.DevCapture;
import onix.mc.ThemedButton;
import onix.mc.VanillaTheme;

/**
 * The main menu. Nothing of the vanilla picture is drawn: the client backdrop
 * replaces the panorama, and the wordmark replaces the logo, splash text and
 * version lines. The game's buttons still do the work, laid out as a narrow
 * centred column: two full-width rows, a split row, and Quit.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	// Button ids the game gives the main menu entries.
	private static final int OPTIONS = 0, SINGLEPLAYER = 1, MULTIPLAYER = 2, QUIT = 4, LANGUAGE = 5, REALMS = 14;
	private static final int WIDE = 196, ROW = 18, GAP = 6;

	/** Top of the first button, in GUI units. */
	@Unique
	private int onix$top() {
		return height / 2 - 6;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void onix$layout(CallbackInfo ci) {
		int left = width / 2 - WIDE / 2, top = onix$top(), step = ROW + GAP;
		int half = (WIDE - GAP) / 2;
		for (int i = 0; i < buttons.size(); i++) {
			ButtonWidget button = buttons.get(i);
			ThemedButton themed = (ThemedButton) button;
			if (button.id == SINGLEPLAYER) themed.onix$bounds(left, top, WIDE, ROW);
			else if (button.id == MULTIPLAYER) themed.onix$bounds(left, top + step, WIDE, ROW);
			else if (button.id == REALMS) themed.onix$bounds(left, top + step * 2, half, ROW);
			else if (button.id == OPTIONS) themed.onix$bounds(left + half + GAP, top + step * 2, WIDE - half - GAP, ROW);
			else if (button.id == QUIT) themed.onix$bounds(left, top + step * 3, WIDE, ROW);
			else if (button.id == LANGUAGE) {
				// The language button keeps its own icon; it sits in the bottom right corner.
				button.x = width - 26;
				button.y = height - 26;
			}
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void onix$title(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		VanillaTheme.title(width, height, onix$top() - 16);
		for (int i = 0; i < buttons.size(); i++) buttons.get(i).render(minecraft, mouseX, mouseY);
		DevCapture.afterTitleFrame(minecraft);
		ci.cancel();
	}
}
