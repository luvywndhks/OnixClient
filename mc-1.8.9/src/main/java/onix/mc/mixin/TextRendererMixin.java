package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.render.TextRenderer;

import onix.core.modules.ConvenienceModules;
import onix.mc.Bridge;

/**
 * Nick Hider: swaps the player's name in text as it is drawn and measured, so
 * chat, the tab list and nametags all show the replacement. Nothing is sent out.
 */
@Mixin(TextRenderer.class)
public class TextRendererMixin {
	@ModifyVariable(method = "draw(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String onix$hideNickDrawn(String text) {
		ConvenienceModules.NickHider m = Bridge.get().onix.nickHider;
		return m.isEnabled() ? m.apply(text) : text;
	}

	@ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String onix$hideNickMeasured(String text) {
		ConvenienceModules.NickHider m = Bridge.get().onix.nickHider;
		return m.isEnabled() ? m.apply(text) : text;
	}
}
