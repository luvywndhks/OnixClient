package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.locale.I18n;

import onix.mc.Bridge;

@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	@Shadow
	public abstract String getTranslationKey();

	/** Numerical Enchantments: the level as a number after the name, instead of a Roman numeral. */
	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	private void onix$numberLevel(int level, CallbackInfoReturnable<String> cir) {
		if (Bridge.get().onix.numericalEnchants.isEnabled()) cir.setReturnValue(I18n.translate(getTranslationKey()) + " " + level);
	}
}
