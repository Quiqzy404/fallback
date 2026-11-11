package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import dev.quiqzy.client.module.ModuleManager;

/** Hit Effects: jeden eigenen Angriff melden (vor dem Senden, damit Crit-Werte noch stimmen). */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "destroyBlock", at = @At("RETURN"), require = 0)
	private void zero$blockBroken(net.minecraft.core.BlockPos pos, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) ModuleManager.SESSION_STATS.onBlockBroken();
	}

	/** Crystal Optimizer: nach dem Senden des Schlags den Kristall bei dir sofort entfernen. */
	@Inject(method = "attack", at = @At("RETURN"), require = 0)
	private void zero$crystalOptimizer(Player player, Entity target, CallbackInfo ci) {
		if (ModuleManager.CRYSTAL_OPTIMIZER.isEnabled() && target instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
				&& net.minecraft.client.Minecraft.getInstance().level != null) {
			net.minecraft.client.Minecraft.getInstance().level.removeEntity(target.getId(), Entity.RemovalReason.KILLED);
		}
	}

	@Inject(method = "attack", at = @At("HEAD"), require = 0)
	private void zero$hitEffects(Player player, Entity target, CallbackInfo ci) {
		try {
			dev.quiqzy.client.hud.HitFx.markHit();
			ModuleManager.VISUAL_FX.onAttack(target);
			ModuleManager.SESSION_STATS.onAttack(target);
			if (player instanceof LocalPlayer lp) {
				ModuleManager.AUTO_WEAPON.beforeAttack(lp, target);
				ModuleManager.W_TAP.onAttack(lp);
				ModuleManager.AUTO_JUMP_RESET.onYouAttack();
				ModuleManager.HIT_EFFECTS.onAttack(lp, target);
				if (!ModuleManager.HIT_EFFECTS.isEnabled()) {
					ModuleManager.TARGET_HUD.onAttack(target);
					if (target instanceof net.minecraft.world.entity.LivingEntity le && le.isBlocking() && lp.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) ModuleManager.TARGET_HUD.onShieldBroken();
				}
			}
		} catch (Throwable ignored) {
		}
	}
}
