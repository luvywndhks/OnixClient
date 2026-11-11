package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.entity.ItemRenderer;

import onix.core.modules.ExtraModules;
import onix.mc.Bridge;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
	/** Global Settings, "Disable Enchantment Glint". */
	@Inject(method = "renderEnchantmentGlint", at = @At("HEAD"), cancellable = true)
	private void onix$noGlint(CallbackInfo ci) {
		if (Bridge.get().onix.global.noGlint.on) ci.cancel();
	}

	/** Glint Colorizer: the glint is the model drawn twice more, tinted with one color; that color is swapped. */
	@ModifyArg(method = "renderEnchantmentGlint", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/ItemRenderer;render(Lnet/minecraft/client/render/model/block/BakedModel;I)V"), index = 1)
	private int onix$glintColor(int vanilla) {
		ExtraModules.GlintColor m = Bridge.get().onix.glintColor;
		return m.isEnabled() ? 0xFF000000 | (m.color.argb & 0xFFFFFF) : vanilla;
	}
}
