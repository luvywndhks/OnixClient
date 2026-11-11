package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.Screen;

import onix.mc.VanillaTheme;

@Mixin(Screen.class)
public class ScreenMixin {
	/** Vanilla screens get the frosted, dimmed backdrop instead of the gradient or the dirt texture. */
	@Inject(method = "renderBackground(I)V", at = @At("HEAD"), cancellable = true)
	private void onix$background(int offset, CallbackInfo ci) {
		VanillaTheme.background();
		ci.cancel();
	}

	/** Development captures only: lets a capture run grab a vanilla screen once it has been drawn. */
	@Inject(method = "render", at = @At("TAIL"))
	private void onix$afterRender(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		onix.mc.DevCapture.afterVanillaFrame();
	}
}
