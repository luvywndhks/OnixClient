package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.Tesselator;

import onix.mc.VanillaTheme;

/**
 * Every vanilla list (worlds, servers, resource packs, languages, key binds)
 * loses its dirt texture and sits on a sheet of frosted glass instead.
 */
@Mixin(ListWidget.class)
public class ListWidgetMixin {
	@Shadow
	protected int minY;
	@Shadow
	protected int maxY;
	@Shadow
	protected int maxX;
	@Shadow
	protected int minX;

	/**
	 * The first thing the list draws is its dirt background. The quad is already
	 * queued, so it is finished with color writes off, which leaves no mark, and
	 * the glass sheet is drawn in its place.
	 */
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 0))
	private void onix$glassInsteadOfDirt(Tesselator tesselator) {
		GlStateManager.colorMask(false, false, false, false);
		tesselator.end();
		GlStateManager.colorMask(true, true, true, true);
		VanillaTheme.listPanel(minX, minY, maxX, maxY);
	}

	/** The dirt strips above and below the list, which hide entries scrolled out of it. */
	@Inject(method = "renderHoleBackground", at = @At("HEAD"), cancellable = true)
	private void onix$edge(int y0, int y1, int alphaTop, int alphaBottom, CallbackInfo ci) {
		VanillaTheme.listEdge(y0, y1);
		ci.cancel();
	}
}
