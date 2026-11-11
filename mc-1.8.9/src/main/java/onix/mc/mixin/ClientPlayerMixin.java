package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;

import onix.mc.Bridge;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerMixin {
	/** Static FOV: the modifier is what widens the view when sprinting or sped up. */
	@Inject(method = "getFovModifier", at = @At("HEAD"), cancellable = true)
	private void onix$staticFov(CallbackInfoReturnable<Float> cir) {
		if (Bridge.get().onix.global.staticFov.on) cir.setReturnValue(1f);
	}
}
