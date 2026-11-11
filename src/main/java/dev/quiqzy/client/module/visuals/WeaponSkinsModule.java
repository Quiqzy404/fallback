package dev.quiqzy.client.module.visuals;

import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.UiSounds;

/**
 * Weapon Skins: Schwerter werden (nur bei dir) zu eigenen 3D-Waffen im ZERO-Stil –
 * Katana, Greatsword, Scythe, Karambit, Butterfly. Orange Teile leuchten im Dunkeln.
 * Tricks: Dreh-/Flip-Animation beim Wechsel auf die Waffe und per Taste (Standard R, unter Steuerung änderbar).
 * Modelle: assets/quiqzy/models/item/weapon_*.json (erzeugt mit tools/make_weapons.py).
 */
public final class WeaponSkinsModule extends Module {
	public final Setting.Choice model = add(new Setting.Choice("model", "Model", "Katana", "Katana", "Greatsword", "Scythe", "Karambit", "Butterfly", "Bayonet", "Flip", "Gut", "Bowie"));
	public final Setting.Bool tricks = add(new Setting.Bool("tricks", "Tricks", true));
	public final Setting.Bool trickOnSwap = add(new Setting.Bool("trick_on_swap", "Trick on Swap", true));
	public final Setting.Int trickSpeed = add(new Setting.Int("trick_speed", "Trick Speed", 100, 50, 200, " %"));
	public final Setting.Bool swingTrail = add(new Setting.Bool("swing_trail", "Swing Trail", true));
	public final Setting.Bool axes = add(new Setting.Bool("axes", "Also Axes", false));

	private long trickStart;
	private String trickModel = "";
	private int lastSlot = -1;
	private boolean lastHeld;

	public WeaponSkinsModule() {
		super("weapon_skins", "Weapon Skins", "Your swords become katanas, scythes and knives with tricks", Category.VISUALS);
	}

	/** Soll dieses Item ersetzt werden? */
	public boolean applies(ItemStack stack) {
		if (!isEnabled() || stack.isEmpty()) return false;
		return stack.is(ItemTags.SWORDS) || (axes.on() && stack.is(ItemTags.AXES));
	}

	/** Kopie des Items mit unserem Modell (das echte Item bleibt unverändert). */
	public ItemStack skin(ItemStack stack) {
		if (!applies(stack)) return stack;
		ItemStack copy = stack.copy();
		copy.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("quiqzy", "weapon_" + model.get().toLowerCase(Locale.ROOT)));
		return copy;
	}

	public boolean isSkinned(ItemStack stack) {
		Identifier id = stack.get(DataComponents.ITEM_MODEL);
		return id != null && id.getNamespace().equals("quiqzy") && id.getPath().startsWith("weapon_");
	}

	/** Trick starten (Taste oder Waffenwechsel). */
	public void trick() {
		if (!isEnabled() || !tricks.on()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !applies(mc.player.getMainHandItem())) return;
		trickStart = System.nanoTime();
		trickModel = model.get();
		UiSounds.click();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		int slot = mc.player.getInventory().getSelectedSlot();
		boolean held = applies(mc.player.getMainHandItem());
		if (trickOnSwap.on() && held && (slot != lastSlot || !lastHeld)) trick();
		lastSlot = slot;
		lastHeld = held;
	}

	private float duration() {
		float base = switch (trickModel) {
			case "Butterfly" -> 1.05f;
			case "Karambit" -> 0.85f;
			case "Scythe" -> 1.1f;
			default -> 0.9f;
		};
		return base * 100f / trickSpeed.get();
	}

	private static float easeInOut(float t) {
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/** Dreht die Waffe in der Ego-Sicht während eines Tricks (vom Mixin vor renderItem aufgerufen). */
	public void applyTrick(PoseStack pose, boolean leftHand) {
		if (trickStart == 0) return;
		float t = (System.nanoTime() - trickStart) / 1.0e9f / duration();
		if (t >= 1f) {
			trickStart = 0;
			return;
		}
		float e = easeInOut(t);
		float side = leftHand ? -1f : 1f;
		// leicht anheben, damit die Drehung um den Griff schön aussieht
		float lift = (float) Math.sin(t * Math.PI) * 0.12f;
		pose.translate(0f, lift, 0f);
		switch (trickModel) {
			case "Butterfly" -> {
				// Flip um die Längsachse (2 Umdrehungen) + kleiner Wurf nach oben
				pose.translate(0f, 0.1f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(side * 720f * e));
				pose.mulPose(Axis.ZP.rotationDegrees(side * 25f * (float) Math.sin(t * Math.PI)));
				pose.translate(0f, -0.1f, 0f);
			}
			case "Flip" -> {
				// Klappmesser: schneller Doppel-Flip um die Längsachse
				pose.mulPose(Axis.ZP.rotationDegrees(side * 720f * e));
			}
			case "Gut", "Bowie", "Bayonet" -> {
				// werfen und fangen: hoch, eine Umdrehung, wieder in die Hand
				pose.translate(0f, (float) Math.sin(t * Math.PI) * 0.25f, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-360f * e));
			}
			case "Karambit" -> {
				// Fingerspin um den Ring
				pose.translate(0f, -0.05f, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-720f * e));
				pose.translate(0f, 0.05f, 0f);
			}
			case "Scythe" -> {
				// große langsame Drehung
				pose.mulPose(Axis.XP.rotationDegrees(-360f * e));
				pose.mulPose(Axis.ZP.rotationDegrees(side * 20f * (float) Math.sin(t * Math.PI)));
			}
			default -> {
				// Katana/Greatsword: Twirl + kurzes Präsentieren
				pose.mulPose(Axis.XP.rotationDegrees(-360f * e));
				pose.mulPose(Axis.YP.rotationDegrees(side * 30f * (float) Math.sin(t * Math.PI)));
			}
		}
	}
}
