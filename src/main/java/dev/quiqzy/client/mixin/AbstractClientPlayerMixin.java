package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.AvatarModule;

/** Avatar-Modul: eigenen Skin (und optional den der anderen) durch die Rook-Figur ersetzen. Nur clientseitig. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void zero$avatar(CallbackInfoReturnable<PlayerSkin> cir) {
		AvatarModule m = ModuleManager.AVATAR;
		if (m == null || !m.isEnabled()) return;
		Object self = this;
		if (self != Minecraft.getInstance().player && !m.others.on()) return;
		PlayerSkin original = cir.getReturnValue();
		if (original != null) cir.setReturnValue(m.apply(original));
	}
}
