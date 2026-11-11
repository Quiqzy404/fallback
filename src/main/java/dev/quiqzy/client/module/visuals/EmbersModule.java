package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.hud.HitFx;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Embers: golden-orange Funken schweben langsam um dich herum (nur bei dir sichtbar). */
public final class EmbersModule extends Module {
	public final Setting.Choice color = add(new Setting.Choice("color", "Color", "Ember", "Ember", "Gold", "Sunset"));
	public final Setting.Int amount = add(new Setting.Int("amount", "Amount", 40, 5, 120));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 10, 4, 24, " b"));
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Embers", "Embers", "Petals", "Both"));
	public final Setting.Bool trail = add(new Setting.Bool("trail", "Stardust Trail", true));
	private net.minecraft.world.phys.Vec3 lastPos;

	public EmbersModule() {
		super("embers", "Embers", "Glowing embers drift through the air around you", Category.VISUALS);
	}

	public int[] palette() {
		return switch (color.get()) {
			case "Gold" -> new int[]{0xFFFFE7A0, 0xFFF2B635};
			case "Sunset" -> new int[]{0xFFFFB0C8, 0xFFF0562C};
			default -> new int[]{0xFFFFC08A, 0xFFF0782C};
		};
	}

	@Override
	protected void onDisable() {
		HitFx.clearEmbers();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		String st = style.get();
		int n = amount.get();
		if (!st.equals("Petals")) HitFx.tickEmbers(mc.player.position(), st.equals("Both") ? n / 2 : n, range.get(), palette());
		if (!st.equals("Embers")) HitFx.tickPetals(mc.player.position(), st.equals("Both") ? n / 2 : n, range.get());
		if (trail.on()) {
			net.minecraft.world.phys.Vec3 pos = mc.player.position();
			if (lastPos != null && pos.distanceToSqr(lastPos) > 0.0004 && mc.player.onGround()) {
				HitFx.trail(pos, palette());
			}
			lastPos = pos;
		}
	}
}
