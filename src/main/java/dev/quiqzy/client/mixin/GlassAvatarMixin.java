package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.ModuleManager;

/** Glass: Rüstung erst NACH dem Füllen der Ausrüstung entfernen (Spieler-Renderer setzt sie zuletzt). */
@Mixin(AvatarRenderer.class)
public abstract class GlassAvatarMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void zero$glassArmor(Avatar entity, AvatarRenderState state, float pt, CallbackInfo ci) {
		var m = ModuleManager.GLASS;
		if (!m.applies(entity)) return;
		state.isInvisible = true;
		state.isInvisibleToPlayer = false;
		if (m.outline.on()) state.outlineColor = m.color.get() & 0xFFFFFF;
		if (m.hideArmor.on()) {
			state.headEquipment = ItemStack.EMPTY;
			state.chestEquipment = ItemStack.EMPTY;
			state.legsEquipment = ItemStack.EMPTY;
			state.feetEquipment = ItemStack.EMPTY;
		}
	}
}
