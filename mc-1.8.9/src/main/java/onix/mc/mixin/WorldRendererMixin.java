package onix.mc.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.WorldRenderer;

import onix.core.modules.PvpModules;
import onix.mc.Bridge;

/** Block Outline: swaps the color and line width the game uses for the targeted block. */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Redirect(method = "renderBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V"))
	private void onix$outlineColor(float r, float g, float b, float a) {
		PvpModules.BlockOutline m = Bridge.get().onix.blockOutline;
		if (!m.isEnabled()) {
			GlStateManager.color4f(r, g, b, a);
			return;
		}
		int c = m.color.argb;
		GlStateManager.color4f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, m.opacity.v);
	}

	@Redirect(method = "renderBlockOutline", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V"))
	private void onix$outlineWidth(float width) {
		PvpModules.BlockOutline m = Bridge.get().onix.blockOutline;
		GL11.glLineWidth(m.isEnabled() ? m.width.v : width);
	}
}
