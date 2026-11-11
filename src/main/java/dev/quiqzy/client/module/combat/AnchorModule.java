package dev.quiqzy.client.module.combat;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Anchor: Rechtsklick auf einen Respawn Anchor ->
 *  1. Glowstone aus der Hotbar nehmen und den Anchor aufladen (nur wenn er noch leer ist)
 *  2. nach "Verzögerung" Ticks aufs Totem wechseln (ohne Totem: leerer Slot oder anderes Item) und nochmal rechtsklicken -> Anchor explodiert
 *
 * Gedrückt halten: solange Rechtsklick auf dem Anchor gehalten wird, wiederholt sich das alle "Repeat Every" Ticks.
 *
 * Läuft am ANFANG des Ticks, damit der Rechtsklick abgefangen wird, bevor Minecraft ihn selbst benutzt.
 * Hinweis: Im Nether explodiert ein Anchor nicht (dort setzt er nur den Spawnpunkt).
 */
public final class AnchorModule extends Module {
	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 1, 0, 10, " t"));
	public final Setting.Bool hold = add(new Setting.Bool("hold", "Hold to Repeat", true));
	public final Setting.Int repeat = add(new Setting.Int("repeat", "Repeat Every", 4, 1, 20, " t"));
	/** Safe Anchor: vor der Explosion einen Glowstone-Block zwischen dich und den Anchor setzen. */
	public final Setting.Bool safe = add(new Setting.Bool("safe", "Safe Anchor", true));
	public final Setting.Bool rotate = add(new Setting.Bool("rotate", "Rotate", true));

	private static final Predicate<ItemStack> GLOWSTONE = stack -> stack.is(Items.GLOWSTONE);
	private static final Predicate<ItemStack> TOTEM = stack -> stack.is(Items.TOTEM_OF_UNDYING);

	/** Ticks bis zur Explosion, -1 = nichts geplant. */
	private int ticksUntilExplode = -1;
	private BlockHitResult target;
	/** Wartezeit bis zur nächsten Runde beim Gedrückthalten. */
	private int cooldown;

	public AnchorModule() {
		super("anchor", "Anchor", "Right-click an anchor: glowstone, totem, boom", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		reset();
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		ticksUntilExplode = -1;
		target = null;
		cooldown = 0;
	}

	@Override
	public void onStartTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null || mc.level == null) {
			reset();
			return;
		}
		if (mc.screen != null) return;

		// Wird Rechtsklick gerade wirklich gedrückt gehalten?
		boolean held = hold.on() && InputUtil.isPhysicallyDown(mc, mc.options.keyUse);
		if (cooldown > 0) cooldown--;

		// Explosion ist geplant -> runterzählen, Rechtsklicks solange verschlucken
		if (ticksUntilExplode >= 0) {
			while (mc.options.keyUse.consumeClick()) {
			}
			mc.options.keyUse.setDown(false);
			if (ticksUntilExplode-- == 0) {
				explode(mc, target);
				target = null;
				cooldown = repeat.get();
			}
			return;
		}

		// Schaut der Spieler auf einen Respawn Anchor?
		BlockState state = null;
		BlockHitResult hit = null;
		if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			BlockState s = mc.level.getBlockState(bhr.getBlockPos());
			if (s.is(Blocks.RESPAWN_ANCHOR)) {
				state = s;
				hit = bhr;
			}
		}

		if (hit == null) {
			// Kein Anchor im Blick: gehaltenen Rechtsklick an Minecraft zurückgeben (z. B. neuen Anchor setzen)
			if (held && !mc.options.keyUse.isDown()) mc.options.keyUse.setDown(true);
			return;
		}

		// Neuer Rechtsklick ODER gedrückt halten und Wartezeit vorbei
		boolean clicked = mc.options.keyUse.consumeClick();
		while (mc.options.keyUse.consumeClick()) {
		}
		// Minecraft soll den gehaltenen Rechtsklick nicht selbst auf dem Anchor benutzen
		mc.options.keyUse.setDown(false);
		if (!clicked && !(held && cooldown == 0)) return;

		int charge = state.getValue(RespawnAnchorBlock.CHARGE);
		if (charge > 0) {
			// schon geladen -> direkt sprengen
			explode(mc, hit);
			cooldown = repeat.get();
			return;
		}

		// 1. Aufladen mit Glowstone
		Inventory inv = mc.player.getInventory();
		int glow = findSlot(inv, GLOWSTONE);
		if (glow == -1) { // kein Glowstone in der Hotbar
			cooldown = repeat.get();
			return;
		}
		inv.setSelectedSlot(glow);
		use(mc, hit);

		// 2. Sprengen (sofort oder nach Verzögerung)
		if (delay.get() == 0) {
			explode(mc, hit);
			cooldown = repeat.get();
		} else {
			target = hit;
			ticksUntilExplode = delay.get() - 1;
		}
	}

	/** Aufs Totem wechseln und den Anchor rechtsklicken -> Explosion. */
	private void explode(Minecraft mc, BlockHitResult hit) {
		if (hit == null || mc.player == null || mc.level == null) return;
		if (!mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) return;
		if (safe.on()) dev.quiqzy.client.util.AnchorUtil.placeShield(mc, hit.getBlockPos(), rotate.on());

		Inventory inv = mc.player.getInventory();
		int slot = findSlot(inv, TOTEM);
		// Kein Totem in der Hotbar: jedes andere Item außer Glowstone sprengt den Anchor auch.
		// Lieber ein leerer Slot, sonst irgendein Slot ohne Glowstone.
		if (slot == -1) slot = findSlot(inv, ItemStack::isEmpty);
		if (slot == -1) slot = findSlot(inv, GLOWSTONE.negate());
		if (slot != -1) inv.setSelectedSlot(slot);

		// Mit Glowstone in der Hand würde er nur weiter aufladen statt explodieren
		if (GLOWSTONE.test(mc.player.getMainHandItem())) return;
		use(mc, hit);
	}

	private static void use(Minecraft mc, BlockHitResult hit) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	/** Erster passender Slot in der Hotbar (0–8), sonst -1. */
	private static int findSlot(Inventory inv, Predicate<ItemStack> test) {
		for (int i = 0; i < 9; i++) {
			if (test.test(inv.getItem(i))) return i;
		}
		return -1;
	}
}
