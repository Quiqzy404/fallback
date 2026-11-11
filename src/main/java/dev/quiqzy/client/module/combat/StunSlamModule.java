package dev.quiqzy.client.module.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.EntityHitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Stun Slam – zwei Modi (umschaltbar):
 *  - "Mace Combo": Schild brechen, nach der Verzögerung Mace.
 *  - "Shield Drain": solange du eine schildende Person anschlägst, viele schnelle Schläge pro Tick aufs Schild.
 *
 * Trigger (was du in der Hand halten musst, damit es auslöst) und das Schild-Brech-Item sind getrennt:
 *  - "Any Item" an: jeder Schlag mit jedem Item löst aus.
 *  - sonst: nur mit einem der "Shield Break Items" (leere Liste = jedes Item).
 * Zum Brechen des Schilds wechselt das Modul automatisch auf ein Shield-Break-Item aus der Hotbar
 * (eins der gewählten, sonst irgendeine Axt – nur eine Axt bricht in Minecraft den Schild), dann Mace.
 *
 * Läuft am ANFANG des Ticks, damit der Klick abgefangen wird, bevor Minecraft ihn selbst verarbeitet.
 */
public final class StunSlamModule extends Module {
	public final Setting.Choice mode = add(new Setting.Choice("mode", "Mode", "Mace Combo", "Mace Combo", "Shield Drain"));
	/** Keybind: Toggle = Taste schaltet an/aus, Hold = Modul nur an, solange die Taste gehalten wird. */
	public final Setting.Choice bindMode = add(new Setting.Choice("bind_mode", "Bind Mode", "Toggle", "Toggle", "Hold"));
	/** Nur auslösen, wenn das Ziel horizontal so nah ist (Blöcke). */
	public final Setting.Int targetDist = add(new Setting.Int("target_dist", "Horizontal Target Dist", 6, 1, 6, " b"));
	/** Nur auf den Mace wechseln, wenn du mindestens so tief gefallen bist (Blöcke). 0 = auch am Boden. */
	public final Setting.Int minFall = add(new Setting.Int("min_fall", "Min Fall Distance", 0, 0, 50, " b"));
	/** Aus = nur Mace-Swap, kein Schildbrechen mit der Axt. */
	public final Setting.Bool stunSlam = add(new Setting.Bool("stun_slam", "Stun Slam", true));
	/** Chance = zufälliger Wurf pro Stun Slam, Hold Key = nur solange der Keybind gehalten wird. */
	public final Setting.Choice stunMode = add(new Setting.Choice("stun_mode", "Stun Slam Mode", "Chance", "Chance", "Hold Key"));
	public final Setting.Int chance = add(new Setting.Int("chance", "Chance", 100, 0, 100, "%"));
	/** Chance mit der Fallhöhe skalieren: große Drops lösen öfter aus. */
	public final Setting.Bool scaleWithFall = add(new Setting.Bool("scale_with_fall", "Scale With Fall", false));
	/** Stun Slam erst ab dieser Fallhöhe (Blöcke). 0 = Modul-Minimum (Min Fall Distance). */
	public final Setting.Int stunMinFall = add(new Setting.Int("stun_min_fall", "Stun Min Fall", 0, 0, 50, " b"));
	/** Bevorzugt einen Mace mit Breach-Verzauberung. */
	public final Setting.Bool breachCheck = add(new Setting.Bool("breach_check", "Breach Check", false));
	/** An = egal welches Item in der Hand löst aus (ignoriert die Item-Liste). */
	public final Setting.Bool anyItem = add(new Setting.Bool("any_item", "Any Item", false));
	/** Items, die zum Auslösen in der Hand liegen müssen UND zum Schildbrechen genommen werden. */
	public final Setting.Items breakers = add(new Setting.Items("breakers", "Shield Break Items",
			"minecraft:netherite_axe", "minecraft:diamond_axe", "minecraft:iron_axe",
			"minecraft:golden_axe", "minecraft:stone_axe", "minecraft:wooden_axe"));
	/** Shield Drain: Schläge pro Tick (20 Ticks/s -> 10 = ~200 CPS). */
	public final Setting.Int hitsPerTick = add(new Setting.Int("hits_per_tick", "Drain Hits / Tick", 10, 1, 20));
	/** Shield Drain: an = Angriff gedrückt halten, aus = draint automatisch beim Anvisieren. */
	public final Setting.Bool holdToDrain = add(new Setting.Bool("hold_to_drain", "Hold to Drain", true));
	public final Setting.Int minDelay = add(new Setting.Int("min_delay", "Min. Delay", 1, 0, 40, " t"));
	public final Setting.Int maxDelay = add(new Setting.Int("max_delay", "Max. Delay", 1, 0, 40, " t"));

	/** Ticks bis zum Mace-Schlag, -1 = nichts geplant. */
	private int ticksUntilMaceHit = -1;

	private static final Predicate<ItemStack> MACE = stack -> stack.is(Items.MACE);
	private static final Predicate<ItemStack> AXE = stack -> stack.is(ItemTags.AXES);

	/** Die gewählten Shield-Break-Items, neu gebaut wenn sich die Auswahl ändert. */
	private Set<Item> breakerItems = new HashSet<>();
	private int breakersVersion = -1;

	public StunSlamModule() {
		super("stun_slam", "Stun Slam", "Shield break + mace, or shield drain", Category.COMBAT);
	}

	/**
	 * Keybind-Druck kommt als toggle() vom ModuleManager. Hier abfangen:
	 *  - Stun Slam Mode "Hold Key": Taste löst nur den Stun aus, Modul bleibt an.
	 *  - Bind Mode "Hold": Taste gedrückt = an, losgelassen = aus (siehe onStartTick).
	 * Klicks im GUI (Taste nicht gedrückt) schalten ganz normal um.
	 */
	@Override
	public void toggle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.getWindow() != null && mc.screen == null && isBindHeld(mc)) {
			if (bindUsedForStun()) return;
			if (bindMode.get().equals("Hold")) {
				if (!isEnabled()) super.toggle();
				return;
			}
		}
		super.toggle();
	}

	@Override
	protected void onEnable() {
		ticksUntilMaceHit = -1;
	}

	@Override
	protected void onDisable() {
		ticksUntilMaceHit = -1;
	}

	@Override
	public void onStartTick(Minecraft mc) {
		// Bind Mode Hold: Taste losgelassen -> Modul aus
		if (bindMode.get().equals("Hold") && !bindUsedForStun() && keybind() >= 0 && !isBindHeld(mc)) {
			setEnabled(false);
			return;
		}
		if (mc.player == null || mc.gameMode == null) {
			ticksUntilMaceHit = -1;
			return;
		}
		if (mc.screen != null) return;

		// Shield-Drain-Modus
		if (mode.get().equals("Shield Drain")) {
			shieldDrain(mc);
			return;
		}

		// Mace-Schlag ist schon geplant -> runterzählen, Klicks solange verschlucken
		if (ticksUntilMaceHit >= 0) {
			if (ticksUntilMaceHit-- == 0) maceHit(mc);
			if (mc.hitResult instanceof EntityHitResult) {
				while (mc.options.keyAttack.consumeClick()) {
				}
			}
			return;
		}

		if (!(mc.hitResult instanceof EntityHitResult hit)) return;
		// Trigger: Any Item an -> alles; sonst nur mit einem gewählten Item (leer = jedes).
		if (!canTrigger(mc.player.getMainHandItem())) return;
		Entity target = hit.getEntity();
		// Ziel zu weit weg (horizontal) oder nicht tief genug gefallen -> normaler Schlag, nichts abfangen
		if (!inHorizontalRange(mc, target)) return;
		double fall = mc.player.fallDistance;
		if (fall < minFall.get()) return;
		if (!mc.options.keyAttack.consumeClick()) return;
		while (mc.options.keyAttack.consumeClick()) {
		}

		if (!(target instanceof LivingEntity living) || !living.isBlocking() || !shouldStun(mc, fall)) {
			// Kein Schild oben (oder Stun Slam diesmal nicht) -> direkt Mace
			maceHit(mc);
			return;
		}

		// Schild oben -> auf ein Schild-Brech-Item wechseln (bricht den Schild), dann Mace
		Inventory inv = mc.player.getInventory();
		int slot = findSlot(inv, this::isBreakWeapon); // eins der gewählten Items
		if (slot == -1) slot = findSlot(inv, AXE);     // sonst irgendeine Axt
		if (slot != -1) inv.setSelectedSlot(slot);
		leftClick(mc);

		int min = Math.min(minDelay.get(), maxDelay.get());
		int max = Math.max(minDelay.get(), maxDelay.get());
		int delay = min + mc.player.getRandom().nextInt(max - min + 1);
		if (delay == 0) maceHit(mc);
		else ticksUntilMaceHit = delay - 1;
	}

	/** Shield Drain: schnelle Schläge aufs Schild. */
	private void shieldDrain(Minecraft mc) {
		if (!(mc.hitResult instanceof EntityHitResult hit)) return;
		if (!(hit.getEntity() instanceof LivingEntity living) || !living.isBlocking()) return;
		if (!inHorizontalRange(mc, living)) return;
		if (!canTrigger(mc.player.getMainHandItem())) return;
		if (holdToDrain.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;

		// Zum Draining ein Schild-Brech-Item/Axt nehmen, falls du gerade was anderes hältst
		Inventory inv = mc.player.getInventory();
		if (!AXE.test(mc.player.getMainHandItem()) && !isBreakWeapon(mc.player.getMainHandItem())) {
			int slot = findSlot(inv, this::isBreakWeapon);
			if (slot == -1) slot = findSlot(inv, AXE);
			if (slot != -1) inv.setSelectedSlot(slot);
		}

		int n = hitsPerTick.get();
		for (int i = 0; i < n; i++) {
			mc.gameMode.attack(mc.player, living);
		}
		mc.player.swing(InteractionHand.MAIN_HAND);
		while (mc.options.keyAttack.consumeClick()) {
		}
	}

	/** Ziel horizontal (ohne Höhe) innerhalb von "Horizontal Target Dist"? */
	private boolean inHorizontalRange(Minecraft mc, Entity target) {
		double dx = target.getX() - mc.player.getX();
		double dz = target.getZ() - mc.player.getZ();
		return Math.sqrt(dx * dx + dz * dz) <= targetDist.get();
	}

	/** Soll dieser Schlag ein Stun Slam (Axt -> Mace) werden? */
	private boolean shouldStun(Minecraft mc, double fall) {
		if (!stunSlam.on()) return false;
		if (stunMinFall.get() > 0 && fall < stunMinFall.get()) return false;
		if (stunMode.get().equals("Hold Key")) return isBindHeld(mc);
		double c = chance.get();
		// Scale With Fall: volle Chance ab 10 Blöcken über dem Minimum, darunter anteilig
		if (scaleWithFall.on()) {
			double base = Math.max(minFall.get(), stunMinFall.get());
			c *= Math.min(1.0, Math.max(0.1, (fall - base) / 10.0));
		}
		return mc.player.getRandom().nextDouble() * 100.0 < c;
	}

	/** Ist der Keybind dieses Moduls gerade physisch gedrückt? */
	public boolean isBindHeld(Minecraft mc) {
		int key = keybind();
		if (key < 0) return false;
		return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
	}

	/** Keybind schaltet das Modul nicht um, wenn er für "Hold Key" gebraucht wird. */
	public boolean bindUsedForStun() {
		return stunSlam.on() && stunMode.get().equals("Hold Key");
	}

	private void rebuildBreakers() {
		int v = breakers.version();
		if (v != breakersVersion) {
			Set<Item> set = new HashSet<>();
			for (String id : breakers.get()) {
				Identifier key = Identifier.tryParse(id);
				if (key != null) BuiltInRegistries.ITEM.getOptional(key).ifPresent(set::add);
			}
			breakerItems = set;
			breakersVersion = v;
		}
	}

	/** Darf der Combo mit diesem Item in der Hand auslösen? */
	private boolean canTrigger(ItemStack stack) {
		if (anyItem.on()) return true;          // egal welches Item
		rebuildBreakers();
		if (breakerItems.isEmpty()) return true; // nichts gewählt -> jedes Item
		return breakerItems.contains(stack.getItem());
	}

	/** Ist das ein gewähltes Schild-Brech-Item? (für den Hotbar-Wechsel) */
	private boolean isBreakWeapon(ItemStack stack) {
		rebuildBreakers();
		return breakerItems.contains(stack.getItem());
	}

	private void maceHit(Minecraft mc) {
		Inventory inv = mc.player.getInventory();
		int mace = -1;
		if (breachCheck.on()) mace = findSlot(inv, s -> MACE.test(s) && hasBreach(s));
		if (mace == -1) mace = findSlot(inv, MACE);
		if (mace == -1) return;
		inv.setSelectedSlot(mace);
		leftClick(mc);
	}

	private static boolean hasBreach(ItemStack stack) {
		ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (Holder<Enchantment> h : ench.keySet()) {
			if (h.is(Enchantments.BREACH)) return true;
		}
		return false;
	}

	/** Erster passender Slot in der Hotbar (0–8), sonst -1. */
	private static int findSlot(Inventory inv, Predicate<ItemStack> test) {
		for (int i = 0; i < 9; i++) {
			if (test.test(inv.getItem(i))) return i;
		}
		return -1;
	}

	private static void leftClick(Minecraft mc) {
		if (mc.hitResult instanceof EntityHitResult hit) {
			mc.gameMode.attack(mc.player, hit.getEntity());
		}
		mc.player.swing(InteractionHand.MAIN_HAND);
	}
}
