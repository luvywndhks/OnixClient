package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.render.entity.LivingEntityRenderer;

import onix.core.modules.PvpModules;
import onix.mc.Bridge;

/**
 * Hurt Color. When an entity is hit, the game writes the tint (1, 0, 0, 0.3)
 * into a buffer with four puts; these replace each of the four numbers.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	private static final String METHOD = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z";
	private static final String PUT = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;";

	private static float channel(float vanilla, int shift) {
		PvpModules.HurtColor m = Bridge.get().onix.hurtColor;
		return m.isEnabled() ? ((m.color.argb >> shift) & 0xFF) / 255f : vanilla;
	}

	@ModifyArg(method = METHOD, at = @At(value = "INVOKE", target = PUT, ordinal = 0))
	private float onix$hurtRed(float v) {
		return channel(v, 16);
	}

	@ModifyArg(method = METHOD, at = @At(value = "INVOKE", target = PUT, ordinal = 1))
	private float onix$hurtGreen(float v) {
		return channel(v, 8);
	}

	@ModifyArg(method = METHOD, at = @At(value = "INVOKE", target = PUT, ordinal = 2))
	private float onix$hurtBlue(float v) {
		return channel(v, 0);
	}

	@ModifyArg(method = METHOD, at = @At(value = "INVOKE", target = PUT, ordinal = 3))
	private float onix$hurtStrength(float v) {
		PvpModules.HurtColor m = Bridge.get().onix.hurtColor;
		return m.isEnabled() ? m.strength.v : v;
	}

	/** Third Person Nametag: the game never draws the tag of the entity the camera is in. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z",
			at = @At("RETURN"), cancellable = true)
	private void onix$ownNametag(net.minecraft.entity.living.LivingEntity entity,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (entity == mc.player && mc.options.perspective != 0 && !mc.options.hideGui
				&& Bridge.get().onix.ownNametag.isEnabled()) cir.setReturnValue(true);
	}
}
