package dev.quiqzy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.HandShaderModule;

/**
 * Hand Shader. Method signatures taken from Iris (branch 1.21.11), which hooks the same methods.
 * require = 0: if Minecraft changes them, Hand Shader simply does nothing instead of crashing.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Unique
	private boolean zero$pushed;

	/** Size + position: move/scale the whole first-person hand rendering. */
	/** Freecam: keine Hand vor der fliegenden Kamera. */
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$freecamHand(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		if (ModuleManager.FREECAM.active() && !ModuleManager.FREECAM.hands.on()) ci.cancel();
	}

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
	private void zero$handPoseStart(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		HandShaderModule m = ModuleManager.HAND_SHADER;
		zero$pushed = m.isEnabled() && m.changesPose();
		if (!zero$pushed) return;

		poseStack.pushPose();
		poseStack.translate(m.offsetX.get() / 100f, m.offsetY.get() / 100f, m.offsetZ.get() / 100f);
		float s = m.size.get() / 100f;
		poseStack.scale(s, s, s);
	}

	@Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 0)
	private void zero$handPoseEnd(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		if (zero$pushed) {
			poseStack.popPose();
			zero$pushed = false;
		}
	}

	/** Weapon Skins: Schwert -> eigenes 3D-Modell (nur fürs Zeichnen). */
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack zero$weaponSkin(ItemStack stack) {
		return ModuleManager.WEAPON_SKINS.skin(stack);
	}

	@Unique
	private boolean zero$trickPushed;

	/** Weapon Skins: Trick-Animation nur auf das Item in der Ego-Sicht. */
	@Inject(method = "renderItem", at = @At("HEAD"), require = 0)
	private void zero$trickStart(net.minecraft.world.entity.LivingEntity entity, ItemStack stack, net.minecraft.world.item.ItemDisplayContext ctx,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		zero$trickPushed = false;
		if (!ctx.firstPerson()) return;
		boolean skinned = ModuleManager.WEAPON_SKINS.isSkinned(stack);
		boolean anim = ModuleManager.ANIMATIONS.isEnabled();
		if (!skinned && !anim) return;
		pose.pushPose();
		zero$trickPushed = true;
		boolean left = ctx == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		if (anim) {
			boolean offhand = entity instanceof net.minecraft.world.entity.player.Player pl && pl.getOffhandItem() == stack;
			ModuleManager.ANIMATIONS.apply(pose, stack, left, offhand);
		}
		if (skinned) ModuleManager.WEAPON_SKINS.applyTrick(pose, left);
	}

	@Inject(method = "renderItem", at = @At("RETURN"), require = 0)
	private void zero$trickEnd(net.minecraft.world.entity.LivingEntity entity, ItemStack stack, net.minecraft.world.item.ItemDisplayContext ctx,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (zero$trickPushed) {
			pose.popPose();
			zero$trickPushed = false;
		}
	}

	/** Item Glow: aufblasbare Farb-Huelle mit exakt derselben Pose wie das Item - sitzt immer auf dem Item. */
	@Redirect(method = "renderItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
			require = 0)
	private void zero$glowOutline(ItemStackRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor) {
		HandShaderModule m = ModuleManager.HAND_SHADER;
		if (m.isEnabled() && m.glow.on()) {
			int color = dev.quiqzy.client.render.HandGlowRenderer.color(m.glowColor.get() & 0xFFFFFF, m.glowOpacity.get());
			dev.quiqzy.client.render.HandGlowRenderer.submitGlow(state, poseStack, collector, color, m.glowWidth.get());
		}
		state.submit(poseStack, collector, light, overlay, outlineColor);
	}

	/** Glint: give the held item the enchantment shimmer (only for rendering, the real item is untouched). */
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack zero$glint(ItemStack stack) {
		zero$heldStack = stack;
		HandShaderModule m = ModuleManager.HAND_SHADER;
		if (!m.isEnabled() || !m.glint.on() || stack.isEmpty()) return stack;
		ItemStack copy = stack.copy();
		copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return copy;
	}

	@Unique
	private ItemStack zero$heldStack;

	@Shadow
	protected abstract void swingArm(float swingProgress, PoseStack poseStack, int i, HumanoidArm arm);

	@Shadow
	protected abstract void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress);

	/** Swing: vanilla equip-offset before the swing path is skipped, the style supplies its own offset. */
	@Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 4),
			require = 0)
	private void zero$animEquip(ItemInHandRenderer instance, PoseStack poseStack, HumanoidArm arm, float equipProgress) {
		if (!ModuleManager.HAND_SHADER.swingAnim()) {
			this.applyItemArmTransform(poseStack, arm, equipProgress);
		}
	}

	/**
	 * Swing: replaces the vanilla swing rotation with one of six styles (Gooba reference).
	 * Crossbows keep vanilla.
	 */
	@Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"),
			require = 0)
	private void zero$swingAnimation(ItemInHandRenderer instance, float swingProgress, PoseStack poseStack, int i, HumanoidArm arm) {
		HandShaderModule m = ModuleManager.HAND_SHADER;
		boolean crossbow = zero$heldStack != null && zero$heldStack.getItem() instanceof CrossbowItem;
		if (!m.swingAnim() || crossbow) {
			this.swingArm(swingProgress, poseStack, i, arm);
			return;
		}

		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float p = Math.min(1f, swingProgress * m.speed.get() / 100f);

		float eased;
		String mode = m.swing.get();
		switch (mode) {
			case "First" -> eased = Mth.sin(p * (float) Math.PI / 2f);            // easeOutSine
			case "Third" -> eased = p;                                            // linear
			default -> eased = 1f + 2.70158f * (p - 1f) * (p - 1f) * (p - 1f)      // easeOutBack
					+ 1.70158f * (p - 1f) * (p - 1f);
		}
		float v = Mth.sin(((double) eased * eased) * Math.PI) * Mth.sin(Mth.sqrt(eased) * (float) Math.PI);
		if ("Third".equals(mode)) v = (float) Math.sin(p * 1.5707963267948966 * 2.0);
		float n = m.intensityValue();

		switch (mode) {
			case "First" -> {
				poseStack.translate(side * 0.56f, -0.52f, -0.72f);
				poseStack.mulPose(Axis.XP.rotationDegrees(v * -180f * n / 10f));
				poseStack.mulPose(Axis.ZN.rotationDegrees(v * (arm == HumanoidArm.RIGHT ? -60f * n / 10f : 60f * n / 10f)));
				poseStack.mulPose(Axis.YP.rotationDegrees(v * (arm == HumanoidArm.RIGHT ? -60f * n / 10f : 60f * n / 10f)));
			}
			case "Second" -> {
				poseStack.translate(side * 0.56f, -0.42f, -0.72f);
				poseStack.mulPose(Axis.XP.rotationDegrees(v * -(24f * n)));
				poseStack.translate(0.0, -0.1, 0.0);
			}
			case "Third" -> {
				poseStack.translate((double) side * 0.7, -0.35, -0.9);
				poseStack.mulPose(Axis.YP.rotationDegrees(side * 60f));
				poseStack.mulPose(Axis.ZP.rotationDegrees(side * -60f));
				poseStack.mulPose(Axis.XP.rotationDegrees(-90f - n * 24f * v));
			}
			case "Fourth" -> {
				poseStack.translate((double) side * (0.2f - 0.1f * v * (0.25f * n)), 0.0, (double) -0.2f - 0.2f * v * (0.25f * n));
				poseStack.translate(side * 0.56f, -0.52f, -0.72f);
				poseStack.mulPose(Axis.XP.rotationDegrees(50f - 4f * v * (0.35f * n)));
				poseStack.mulPose(Axis.YP.rotationDegrees(side * (-60f + 0.6f * n * v)));
				poseStack.mulPose(Axis.ZP.rotationDegrees(side * (110f + 40f * v * (0.2f * n))));
			}
			case "Fifth" -> {
				poseStack.translate(side * 0.56f, -0.42f, -0.72f);
				poseStack.mulPose(Axis.XP.rotationDegrees(0.0f + eased * 360f));
				poseStack.translate(0.0, -0.1, 0.0);
			}
			case "Sixth" -> {
				poseStack.translate((double) side * 0.7, -0.35, -0.9);
				poseStack.mulPose(Axis.YP.rotationDegrees(side * 65f));
				poseStack.mulPose(Axis.ZP.rotationDegrees(side * -10f));
				poseStack.mulPose(Axis.XP.rotationDegrees(-90f - n * 24f * v));
			}
		}
	}
}
