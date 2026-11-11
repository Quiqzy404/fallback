package dev.quiqzy.client.module.combat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;
import dev.quiqzy.client.util.Targets;

/** Kill Aura: schlägt automatisch das nächste Ziel in Reichweite, sobald der Schlag aufgeladen ist. */
public final class KillAuraModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 3, 1, 6, " b"));
	public final Setting.Int fov = add(new Setting.Int("fov", "FOV", 360, 30, 360, "\u00b0"));
	public final Setting.Int cooldown = add(new Setting.Int("cooldown", "Charge", 95, 0, 100, " %"));
	public final Setting.Choice rotate = add(new Setting.Choice("rotate", "Rotate", "Smooth", "Smooth", "Snap", "None"));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));
	public final Setting.Bool animals = add(new Setting.Bool("animals", "Animals", false));
	public final Setting.Bool walls = add(new Setting.Bool("walls", "Through Walls", false));
	public final Setting.Bool weaponOnly = add(new Setting.Bool("weapon_only", "Weapon Only", true));
	public final Setting.Bool pauseEating = add(new Setting.Bool("pause_eating", "Pause While Eating", true));

	public LivingEntity target;

	public KillAuraModule() {
		super("kill_aura", "Kill Aura", "Hits the closest target when your swing is charged", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		target = null;
		if (p == null || mc.screen != null || mc.gameMode == null) return;
		if (pauseEating.on() && p.isUsingItem()) return;
		if (weaponOnly.on() && !AutoWeaponModule.isWeapon(p.getMainHandItem())) return;
		List<LivingEntity> list = Targets.find(mc, range.get() + 2, fov.get(), players.on(), mobs.on(), animals.on(), walls.on());
		if (list.isEmpty()) return;
		target = list.get(0);
		if (!rotate.get().equals("None")) {
			float[] a = Targets.angles(p, Targets.aimPoint(p, target));
			Targets.turn(p, a[0], a[1], rotate.get().equals("Snap") ? 0f : 30f, true);
		}
		if (Targets.distTo(p, target) <= range.get() && Targets.cooled(p, cooldown.get())) Targets.hit(mc, target);
	}
}
