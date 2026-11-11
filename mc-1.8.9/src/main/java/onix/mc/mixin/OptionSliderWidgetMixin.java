package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.widget.OptionSliderWidget;

import onix.mc.SliderValue;

@Mixin(OptionSliderWidget.class)
public class OptionSliderWidgetMixin implements SliderValue {
	@Shadow
	private float value;

	@Override
	public float onix$sliderValue() {
		return value;
	}

	/** The vanilla knob is two textured pieces; the themed slider shows its value as a fill instead. */
	@Redirect(method = "renderBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/widget/OptionSliderWidget;drawTexture(IIIIII)V"))
	private void onix$noKnob(OptionSliderWidget self, int x, int y, int u, int v, int w, int h) {
	}
}
