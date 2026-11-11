package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/** Hitboxes: macht die Treffer-Fläche anderer etwas größer, damit dein Fadenkreuz sie leichter trifft. */
public final class HitboxesModule extends Module {
	public final Setting.Int expand = add(new Setting.Int("expand", "Expand", 2, 1, 10, " /10 b"));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));

	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Slightly bigger hitboxes for your crosshair", Category.COMBAT);
	}

	public float extra(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null || !Targets.valid(mc.player, e, players.on(), mobs.on(), mobs.on())) return 0f;
		return expand.get() / 10f;
	}
}
