package dev.quiqzy.client.module.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Target HUD: Panel direkt neben deinem Gegner (oder unter dem Fadenkreuz) mit Kopf, Name, Leben, Waffe,
 * Entfernung, Lebensbalken, Combo, Treffer (gegeben–kassiert), Vorsprung und Schild-Cooldown.
 * Ghost: leuchtender Umriss, wo der Gegner laut Server wirklich steht.
 */
public final class TargetHudModule extends Module {
	public final Setting.Choice position = add(new Setting.Choice("position", "Position", "Next to Crosshair", "Next to Crosshair", "Below Crosshair", "Follow Target"));
	public final Setting.Bool playersOnly = add(new Setting.Bool("players_only", "Players Only", false));
	public final Setting.Bool ghost = add(new Setting.Bool("ghost", "Real Position Ghost", true));

	private LivingEntity target;
	private long lastHit;
	public int hitsGiven;
	public int hitsTaken;
	public int combo;
	/** Bis wann das Schild des Ziels nach einem Axt-Treffer deaktiviert ist (Vanilla: 5 s). */
	public long shieldUntil;
	private int lastHurtTime;

	public TargetHudModule() {
		super("target_hud", "Target HUD", "Fight info panel right next to whoever you hit", Category.HUD);
	}

	/** Du hast etwas getroffen. */
	public void onAttack(Entity e) {
		if (!(e instanceof LivingEntity le)) return;
		if (le != target) {
			hitsGiven = 0;
			hitsTaken = 0;
			combo = 0;
			shieldUntil = 0;
		}
		target = le;
		lastHit = System.currentTimeMillis();
		hitsGiven++;
		combo++;
	}

	/** Du hast mit der Axt ein Schild gebrochen. */
	public void onShieldBroken() {
		shieldUntil = System.currentTimeMillis() + 5000;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		// Selbst getroffen? (neuer Treffer = hurtTime springt hoch)
		int ht = p.hurtTime;
		if (ht > lastHurtTime && ht >= 9 && target != null) {
			LivingEntity by = p.getLastHurtByMob();
			if (by == null || by == target) {
				hitsTaken++;
				combo = 0;
			}
		}
		lastHurtTime = ht;
	}

	/** Aktuelles Ziel: zuletzt getroffen (8 s) oder was du gerade anschaust. */
	public LivingEntity target(Minecraft mc) {
		if (!isEnabled() || mc.player == null) return null;
		if (target != null && (target.isRemoved() || target.isDeadOrDying() || System.currentTimeMillis() - lastHit > 8000 || target.distanceTo(mc.player) > 24)) {
			target = null;
		}
		if (target != null) return target;
		if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le) {
			if (!playersOnly.on() || le instanceof Player) return le;
		}
		return null;
	}

	/** Kurzes Waffen-Etikett für das Panel. */
	public static String weapon(LivingEntity e) {
		ItemStack s = e.getMainHandItem();
		if (s.isEmpty()) return "FIST";
		if (s.is(ItemTags.SWORDS)) return "SWORD";
		if (s.is(ItemTags.AXES)) return "AXE";
		if (s.is(Items.MACE)) return "MACE";
		if (s.is(Items.TRIDENT)) return "TRIDENT";
		if (s.getItem() instanceof BowItem) return "BOW";
		if (s.getItem() instanceof CrossbowItem) return "CROSSBOW";
		if (s.is(Items.END_CRYSTAL)) return "CRYSTAL";
		if (s.is(Items.TOTEM_OF_UNDYING)) return "TOTEM";
		String n = s.getHoverName().getString().toUpperCase(java.util.Locale.ROOT);
		return n.length() > 10 ? n.substring(0, 10) : n;
	}
}
