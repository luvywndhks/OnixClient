package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GameGui;

import onix.mc.Bridge;

@Mixin(GameGui.class)
public class GameGuiMixin {
	/** After the vanilla HUD, so client elements sit on top of it. */
	@Inject(method = "render", at = @At("TAIL"))
	private void onix$renderHud(float tickDelta, CallbackInfo ci) {
		Bridge.get().onHudRender();
	}

	/**
	 * Custom Crosshair: remember whether the game wanted a crosshair this frame, then
	 * hide the vanilla one. The module draws its own only when the game wanted one.
	 */
	@Inject(method = "hasCrosshair", at = @At("RETURN"), cancellable = true)
	private void onix$crosshair(CallbackInfoReturnable<Boolean> cir) {
		Bridge bridge = Bridge.get();
		bridge.crosshairWanted = cir.getReturnValue();
		if (bridge.onix.crosshair.isEnabled()) cir.setReturnValue(false);
	}

	/** Global Settings, "Disable Vignette". */
	@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
	private void onix$noVignette(CallbackInfo ci) {
		if (Bridge.get().onix.global.noVignette.on) ci.cancel();
	}

	/** Global Settings, "Hide Pumpkin Overlay". */
	@Inject(method = "renderPumpkinOverlay", at = @At("HEAD"), cancellable = true)
	private void onix$noPumpkin(CallbackInfo ci) {
		if (Bridge.get().onix.global.noPumpkin.on) ci.cancel();
	}

	/** Global Settings, "Hide Scoreboard". */
	@Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
	private void onix$hideScoreboard(CallbackInfo ci) {
		if (Bridge.get().onix.global.hideScoreboard.on) ci.cancel();
	}
}
