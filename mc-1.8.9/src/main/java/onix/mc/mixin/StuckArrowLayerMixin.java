package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.entity.layer.StuckArrowLayer;

import onix.mc.Bridge;

@Mixin(StuckArrowLayer.class)
public class StuckArrowLayerMixin {
	/** Global Settings, "Hide Arrows Stuck In Players". */
	@Inject(method = "render*", at = @At("HEAD"), cancellable = true)
	private void onix$noStuckArrows(CallbackInfo ci) {
		if (Bridge.get().onix.global.noStuckArrows.on) ci.cancel();
	}
}
