package onix.mc.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.Block;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.SnowLayerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CarrotOnAStickItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SwordItem;

import onix.core.Onix;
import onix.core.modules.AnimationModules;
import onix.core.modules.ItemSizeModule;
import onix.mc.Bridge;

/**
 * First-person hand hooks: Item Size, 1.7 Animations and Lunar Old Animation.
 *
 * In renderInFirstPerson the game calls applyFirstPersonTransform(equip, swing)
 * five times, one per case: 0 idle while using, 1 eating or drinking,
 * 2 blocking, 3 drawing a bow, 4 the normal case. 1.8 passes swing = 0 for
 * cases 1 to 3, which is why the hand no longer swings while an item is used.
 */
@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {
	private static final String TRANSFORM = "Lnet/minecraft/client/render/ItemInHandRenderer;applyFirstPersonTransform(FF)V";

	@Shadow
	@Final
	private Minecraft minecraft;

	@Unique
	private float onix$swing;

	@Inject(method = "renderInFirstPerson", at = @At("HEAD"))
	private void onix$captureSwing(float tickDelta, CallbackInfo ci) {
		LocalClientPlayerEntity player = minecraft.player;
		onix$swing = player == null ? 0f : player.getAttackAnimationProgress(tickDelta);
	}

	// ------------------------------------------------------ 1.7 animations

	@ModifyArg(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = TRANSFORM, ordinal = 1), index = 1)
	private float onix$eatSwing(float swing) {
		AnimationModules.OldAnimations m = Bridge.get().onix.oldAnimations;
		return m.isEnabled() && m.eating.on ? onix$swing : swing;
	}

	@ModifyArg(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = TRANSFORM, ordinal = 2), index = 1)
	private float onix$blockSwing(float swing) {
		AnimationModules.OldAnimations m = Bridge.get().onix.oldAnimations;
		return m.isEnabled() && m.blockHit.on ? onix$swing : swing;
	}

	@ModifyArg(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = TRANSFORM, ordinal = 3), index = 1)
	private float onix$bowSwing(float swing) {
		AnimationModules.OldAnimations m = Bridge.get().onix.oldAnimations;
		return m.isEnabled() && m.bow.on ? onix$swing : swing;
	}

	// ------------------------------------------------- Lunar old animation

	/** Old Lunar holds the sword a little higher while blocking. */
	@ModifyArg(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = TRANSFORM, ordinal = 2), index = 0)
	private float onix$lunarBlockEquip(float equip) {
		AnimationModules.LunarAnimation m = Bridge.get().onix.lunarAnimation;
		return m.isEnabled() && m.blockHit.on ? 0.2f : equip;
	}

	@Inject(method = "applyFirstPersonTransform", at = @At("HEAD"))
	private void onix$lunarItemPositions(float equip, float swing, CallbackInfo ci) {
		AnimationModules.LunarAnimation m = Bridge.get().onix.lunarAnimation;
		if (!m.isEnabled() || !m.itemPositions.on || minecraft.player == null) return;
		ItemStack held = minecraft.player.getItemInHand();
		Item item = held == null ? null : held.getItem();
		if (item == null) return;
		if (item instanceof SwordItem) {
			GL11.glTranslatef(0.0f, 0.0f, -0.02f);
			GL11.glRotatef(1.0f, 0.0f, 0.0f, -0.1f);
		} else if (item instanceof PotionItem) {
			GL11.glTranslatef(-0.0225f, -0.02f, 0.0f);
			GL11.glRotatef(1.0f, 0.0f, 0.0f, 0.1f);
		} else if (item instanceof FishingRodItem || item instanceof CarrotOnAStickItem) {
			GL11.glTranslatef(0.08f, -0.0275f, -0.33f);
			GL11.glScalef(0.95f, 1.0f, 1.0f);
		} else if (item instanceof BowItem) {
			GL11.glRotatef(0.9f, 0.0f, 0.001f, 0.0f);
		} else if (item instanceof BlockItem) {
			Block block = ((BlockItem) item).getBlock();
			if (block instanceof CarpetBlock || block instanceof SnowLayerBlock) GL11.glTranslatef(0.0f, -0.25f, 0.0f);
		}
	}

	@Inject(method = "applySwordBlocking", at = @At("RETURN"))
	private void onix$lunarBlockPose(CallbackInfo ci) {
		AnimationModules.LunarAnimation m = Bridge.get().onix.lunarAnimation;
		if (!m.isEnabled() || !m.blockHit.on) return;
		GL11.glTranslatef(-0.55f, 0.2f, 0.1f);
		GL11.glScalef(0.85f, 0.85f, 0.85f);
		GL11.glRotatef(1.0f, 0.0f, 0.0f, -1.0f);
		GL11.glRotatef(1.0f, 0.25f, 0.0f, 0.0f);
		GL11.glRotatef(2.0f, 0.0f, 2.0f, 0.0f);
	}

	@Inject(method = "applyBowNocking", at = @At("HEAD"))
	private void onix$lunarBowPose(CallbackInfo ci) {
		AnimationModules.LunarAnimation m = Bridge.get().onix.lunarAnimation;
		if (!m.isEnabled() || !m.itemPositions.on) return;
		GL11.glTranslatef(-0.2f, 0.0f, -0.175f);
		GL11.glRotatef(1.0f, 0.0f, 0.0f, -1.25f);
	}

	// ----------------------------------------------------------- item size

	/** Runs right before the item is drawn, inside the matrix the game pushed for it. */
	@Inject(method = "renderInFirstPerson", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/ItemInHandRenderer;render(Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resource/model/ModelTransformations$Type;)V"))
	private void onix$itemSize(float tickDelta, CallbackInfo ci) {
		ItemSizeModule m = Onix.get().itemSize;
		if (!m.isEnabled()) return;
		GL11.glTranslatef(m.x.v, m.y.v, m.z.v);
		float s = m.size.v;
		GL11.glScalef(s, s, s);
	}

	/** Global Settings, "Low Fire Overlay": the flames are drawn lower so they block less of the view. */
	@Inject(method = "renderOnFireEffect", at = @At("HEAD"))
	private void onix$lowFireIn(float tickDelta, CallbackInfo ci) {
		GL11.glPushMatrix();
		if (Bridge.get().onix.global.lowFire.on) GL11.glTranslatef(0f, -0.3f, 0f);
	}

	@Inject(method = "renderOnFireEffect", at = @At("RETURN"))
	private void onix$lowFireOut(float tickDelta, CallbackInfo ci) {
		GL11.glPopMatrix();
	}
}
