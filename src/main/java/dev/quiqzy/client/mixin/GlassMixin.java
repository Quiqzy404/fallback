package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import dev.quiqzy.client.module.ModuleManager;

/** Glass: Körper wird wie "unsichtbar, aber für dich sichtbar" gezeichnet = durchscheinend; Umriss leuchtet. */
@Mixin(LivingEntityRenderer.class)
public abstract class GlassMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void zero$glass(LivingEntity entity, LivingEntityRenderState state, float pt, CallbackInfo ci) {
		var m = ModuleManager.GLASS;
		if (!m.applies(entity)) return;
		state.isInvisible = true;
		state.isInvisibleToPlayer = false;
		if (m.outline.on()) state.outlineColor = m.color.get() & 0xFFFFFF;
		if (m.hideArmor.on() && state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState h) {
			h.headEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			h.chestEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			h.legsEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			h.feetEquipment = net.minecraft.world.item.ItemStack.EMPTY;
		}
	}
}
