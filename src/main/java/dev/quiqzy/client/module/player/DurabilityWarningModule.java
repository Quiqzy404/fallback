package dev.quiqzy.client.module.player;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Durability Warning: Hinweis + Ton, wenn Rüstung oder Werkzeug in der Hand fast kaputt ist. */
public final class DurabilityWarningModule extends Module {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public final Setting.Int percent = add(new Setting.Int("percent", "Warn Below", 10, 1, 50, " %"));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound", true));

	/** Pro Slot: welches Item schon gewarnt wurde (damit es nicht dauernd piept). */
	private final Map<EquipmentSlot, String> warned = new EnumMap<>(EquipmentSlot.class);
	private int timer;

	public DurabilityWarningModule() {
		super("durability_warning", "Durability Warning", "Warns before armor or tools break", Category.PLAYER);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || ++timer < 10) return;
		timer = 0;

		for (EquipmentSlot slot : SLOTS) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.isEmpty() || !s.isDamageableItem()) {
				warned.remove(slot);
				continue;
			}
			int left = s.getMaxDamage() - s.getDamageValue();
			int pct = Math.round(left * 100f / s.getMaxDamage());
			String key = s.getItem().toString();
			if (pct > percent.get()) {
				warned.remove(slot);
				continue;
			}
			if (key.equals(warned.get(slot))) continue;
			warned.put(slot, key);

			p.displayClientMessage(Component.literal("⚠ " + s.getHoverName().getString() + " is almost broken! (" + left + " left)")
					.withStyle(ChatFormatting.RED), true);
			if (sound.on()) p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.5f);
		}
	}

	@Override
	protected void onDisable() {
		warned.clear();
	}
}
