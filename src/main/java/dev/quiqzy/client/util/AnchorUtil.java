package dev.quiqzy.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

/** Safe Anchor: Glowstone-Block zwischen dich und den Anchor setzen, damit die Explosion abgefangen wird. */
public final class AnchorUtil {
	private AnchorUtil() {
	}

	/** Für den Selbsttest: wie oft ein Schild gesetzt wurde. */
	public static int shieldsPlaced;

	/** Die Stelle zwischen Anchor und Spieler (auf der Seite zu dir, auf deiner Fußhöhe wenn möglich). */
	public static BlockPos shieldPos(LocalPlayer p, BlockPos anchor) {
		double dx = p.getX() - (anchor.getX() + 0.5), dz = p.getZ() - (anchor.getZ() + 0.5);
		Direction d = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockPos side = anchor.relative(d);
		int feet = p.blockPosition().getY();
		// liegt der Anchor tiefer als du, kommt der Schild eins höher, damit er deinen Körper abdeckt
		if (feet > anchor.getY()) side = side.above(Math.min(1, feet - anchor.getY()));
		return side;
	}

	/** Setzt den Schutzblock mit Glowstone aus der Hotbar. Gibt true zurück, wenn ein Block gesetzt wurde. */
	public static boolean placeShield(Minecraft mc, BlockPos anchor, boolean rotate) {
		LocalPlayer p = mc.player;
		BlockPos s = shieldPos(p, anchor);
		// Anchor direkt neben dir: der Platz dazwischen bist du selbst -> Schild auf Kopfhöhe (deckt Kopf/Oberkörper)
		if (p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(s)) || !Place.canPlace(mc, s)) s = anchor.above();
		if (p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(s)) || !Place.canPlace(mc, s)) return false;
		int glow = Targets.hotbar(p, st -> st.is(Items.GLOWSTONE));
		if (glow < 0) return false;
		int prev = p.getInventory().getSelectedSlot();
		p.getInventory().setSelectedSlot(glow);
		boolean ok = Place.at(mc, s, rotate);
		if (ok) shieldsPlaced++;
		p.getInventory().setSelectedSlot(prev);
		return ok;
	}
}
