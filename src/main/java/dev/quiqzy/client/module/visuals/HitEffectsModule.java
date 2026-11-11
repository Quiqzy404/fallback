package dev.quiqzy.client.module.visuals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.hud.HitFx;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.UiSounds;

/**
 * Hit Effects: Treffer fühlen sich besser an – im ZERO-Orange.
 *  - Ripple: leuchtende Ring-Welle am Treffpunkt
 *  - Spark Splash: Funken spritzen aus dem Treffpunkt
 *  - Crit Stars: bei kritischen Treffern fliegen funkelnde Sterne
 *  - Kill Beam: Lichtsäule, wenn dein Ziel stirbt
 *  - Screen Glow: weiches orange Leuchten am Bildschirmrand
 *  - Hit Sounds: eigene kurze Treffer-Töne
 * Nur bei dir sichtbar (rein clientseitig).
 */
public final class HitEffectsModule extends Module {
	public final Setting.Choice color = add(new Setting.Choice("color", "Color", "Ember", "Ember", "Gold", "Sunset"));
	public final Setting.Int intensity = add(new Setting.Int("intensity", "Intensity", 100, 30, 200, " %"));
	public final Setting.Bool ripple = add(new Setting.Bool("ripple", "Ripple", true));
	public final Setting.Bool splash = add(new Setting.Bool("splash", "Spark Splash", true));
	public final Setting.Bool critStars = add(new Setting.Bool("crit_stars", "Crit Stars", true));
	public final Setting.Bool killBeam = add(new Setting.Bool("kill_beam", "Kill Beam", true));
	public final Setting.Bool screenGlow = add(new Setting.Bool("screen_glow", "Screen Glow", true));
	public final Setting.Bool sounds = add(new Setting.Bool("sounds", "Hit Sounds", true));
	public final Setting.Bool hurtColor = add(new Setting.Bool("hurt_color", "Hit Color", true));
	public final Setting.Bool shieldFx = add(new Setting.Bool("shield_fx", "Shield Breaker", true));
	public final Setting.Bool afterglow = add(new Setting.Bool("afterglow", "Afterglow", true));

	/** Vanilla-Overlay-Textur (rotes Aufblinken), gesetzt vom OverlayTextureMixin. */
	public static net.minecraft.client.renderer.texture.DynamicTexture overlayTexture;
	private int paintedColor = -1;

	/** Zuletzt getroffene Ziele, um Kills zu erkennen. */
	private record Tracked(Entity entity, long until) {
	}

	private final List<Tracked> tracked = new ArrayList<>();

	public HitEffectsModule() {
		super("hit_effects", "Hit Effects", "Ripples, sparks, crit stars and kill beams on every hit", Category.VISUALS);
	}

	@Override
	protected void onDisable() {
		tracked.clear();
		HitFx.clear();
		paintOverlay();
	}

	/** Farben (hell, kräftig) je nach Auswahl. */
	public int[] palette() {
		int[] p = switch (color.get()) {
			case "Gold" -> new int[]{0xFFFFE7A0, 0xFFF2B635};
			case "Sunset" -> new int[]{0xFFFFB0C8, 0xFFF0562C};
			default -> new int[]{0xFFFFC08A, 0xFFF0782C};
		};
		return new int[]{dev.quiqzy.client.util.Theme.tint(p[0]), dev.quiqzy.client.util.Theme.tint(p[1])};
	}

	public float strength() {
		return intensity.get() / 100f;
	}

	/** Vom Mixin auf MultiPlayerGameMode.attack: du hast gerade etwas angegriffen. */
	public void onAttack(LocalPlayer player, Entity target) {
		if (!isEnabled() || player == null || target == null) return;
		boolean crit = isCrit(player, target);
		boolean shieldBreak = target instanceof LivingEntity le0 && le0.isBlocking()
				&& player.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES);
		dev.quiqzy.client.module.ModuleManager.TARGET_HUD.onAttack(target);
		if (shieldBreak) dev.quiqzy.client.module.ModuleManager.TARGET_HUD.onShieldBroken();

		// Treffpunkt: auf der Hitbox des Ziels, Richtung Spieler, etwa auf Brusthöhe
		Vec3 center = target.getBoundingBox().getCenter();
		Vec3 eye = player.getEyePosition();
		Vec3 toPlayer = eye.subtract(center).normalize();
		double rx = target.getBbWidth() * 0.5;
		Vec3 hit = center.add(toPlayer.x * rx, Math.min(0.25, toPlayer.y * 0.3), toPlayer.z * rx);

		HitFx.hit(hit, crit, this);
		if (shieldBreak && shieldFx.on()) HitFx.shieldBreak(hit, this);

		if (sounds.on()) UiSounds.hit(crit);
		if (target instanceof LivingEntity) {
			tracked.removeIf(t -> t.entity() == target);
			tracked.add(new Tracked(target, System.currentTimeMillis() + 2500));
		}
	}

	/** Vanilla-Bedingungen für einen kritischen Treffer. */
	private static boolean isCrit(LocalPlayer p, Entity target) {
		return target instanceof LivingEntity
				&& p.fallDistance > 0.0
				&& !p.onGround()
				&& !p.onClimbable()
				&& !p.isInWater()
				&& !p.hasEffect(MobEffects.BLINDNESS)
				&& !p.isPassenger()
				&& !p.isSprinting()
				&& p.getAttackStrengthScale(0.5f) > 0.9f;
	}

	/** Treffer-Aufblinken umfärben (Vanilla rot -> Client-Farbe), nur wenn sich etwas ändert. */
	private void paintOverlay() {
		if (overlayTexture == null) return;
		int rgb = isEnabled() && hurtColor.on() ? (palette()[1] & 0xFFFFFF) : 0xFF0000;
		if (rgb == paintedColor) return;
		var img = overlayTexture.getPixels();
		if (img == null) return;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				img.setPixel(x, y, 0xB2000000 | rgb);
			}
		}
		overlayTexture.upload();
		paintedColor = rgb;
	}

	@Override
	protected void onEnable() {
		paintOverlay();
	}

	@Override
	public void onTick(Minecraft mc) {
		paintOverlay();
		long now = System.currentTimeMillis();
		Iterator<Tracked> it = tracked.iterator();
		while (it.hasNext()) {
			Tracked t = it.next();
			Entity e = t.entity();
			boolean dead = e instanceof LivingEntity le && (le.isDeadOrDying() || (e.isRemoved() && le.getHealth() <= 0f));
			if (dead) {
				if (killBeam.on()) HitFx.kill(e.position(), this);
				if (afterglow.on()) HitFx.afterglow(e.getBoundingBox(), this);
				if (sounds.on()) UiSounds.kill();
				it.remove();
			} else if (now > t.until()) {
				it.remove();
			}
		}
	}
}
