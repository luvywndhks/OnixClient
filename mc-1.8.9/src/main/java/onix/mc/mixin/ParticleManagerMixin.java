package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.state.BlockState;
import net.minecraft.client.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import onix.core.modules.ExtraModules;
import onix.mc.Bridge;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Shadow
	public abstract void addEmitter(Entity entity, ParticleType type);

	@Unique
	private boolean onix$multiplying;

	/** Global Settings, "Disable Block Break Particles": the burst when a block breaks. */
	@Inject(method = "addBlockMiningParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/BlockState;)V",
			at = @At("HEAD"), cancellable = true)
	private void onix$noBreakBurst(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (Bridge.get().onix.global.noBreakParticles.on) ci.cancel();
	}

	/** And the chips that fly while a block is being mined. */
	@Inject(method = "addBlockMiningParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)V",
			at = @At("HEAD"), cancellable = true)
	private void onix$noMiningChips(BlockPos pos, Direction face, CallbackInfo ci) {
		if (Bridge.get().onix.global.noBreakParticles.on) ci.cancel();
	}

	/** Particle Multiplier: a hit starts one burst of stars; start a few more of the same. */
	@Inject(method = "addEmitter", at = @At("HEAD"))
	private void onix$moreHitParticles(Entity entity, ParticleType type, CallbackInfo ci) {
		if (onix$multiplying) return;
		ExtraModules.ParticleMultiplier m = Bridge.get().onix.particleMultiplier;
		int extra = m.multiplier() - 1;
		if (extra <= 0) return;
		if (type != ParticleType.CRIT && !(type == ParticleType.CRIT_MAGIC && m.always.on)) return;
		onix$multiplying = true;
		try {
			for (int i = 0; i < extra; i++) addEmitter(entity, type);
		} finally {
			onix$multiplying = false;
		}
	}
}
