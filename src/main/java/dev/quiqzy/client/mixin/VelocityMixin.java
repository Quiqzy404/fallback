package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.ModuleManager;

/** Velocity: nachdem Minecraft den Rückstoß gesetzt hat, wird er auf den eingestellten Anteil verkleinert. */
@Mixin(ClientPacketListener.class)
public abstract class VelocityMixin {
	@Unique
	private Vec3 zero$before;

	@Inject(method = "handleSetEntityMotion", at = @At("RETURN"), require = 0)
	private void zero$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		var m = ModuleManager.VELOCITY;
		if (!m.isEnabled() || mc.player == null || !mc.isSameThread() || packet.getId() != mc.player.getId()) return;
		Vec3 v = mc.player.getDeltaMovement();
		double h = m.horizontal.get() / 100.0, y = m.vertical.get() / 100.0;
		mc.player.setDeltaMovement(v.x * h, v.y * y, v.z * h);
	}

	@Inject(method = "handleExplosion", at = @At("HEAD"), require = 0)
	private void zero$explBefore(ClientboundExplodePacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		zero$before = mc.player != null && mc.isSameThread() ? mc.player.getDeltaMovement() : null;
	}

	@Inject(method = "handleExplosion", at = @At("RETURN"), require = 0)
	private void zero$explAfter(ClientboundExplodePacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		var m = ModuleManager.VELOCITY;
		if (zero$before == null || mc.player == null || !m.isEnabled() || !m.explosions.on()) return;
		Vec3 add = mc.player.getDeltaMovement().subtract(zero$before);
		double h = m.horizontal.get() / 100.0, y = m.vertical.get() / 100.0;
		mc.player.setDeltaMovement(zero$before.add(add.x * h, add.y * y, add.z * h));
		zero$before = null;
	}
}
