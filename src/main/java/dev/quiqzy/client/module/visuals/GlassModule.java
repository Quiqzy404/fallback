package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Glass: Spieler werden zu durchscheinendem Glas mit leuchtendem Umriss (nur bei dir sichtbar). */
public final class GlassModule extends Module {
	public final Setting.Bool self = add(new Setting.Bool("self", "Yourself", true));
	public final Setting.Bool others = add(new Setting.Bool("others", "Other Players", false));
	public final Setting.Bool outline = add(new Setting.Bool("outline", "Glow Outline", true));
	public final Setting.Bool hideArmor = add(new Setting.Bool("hide_armor", "Hide Armor", true));
	public final Setting.Color color = add(new Setting.Color("color", "Outline Colour", 0xFFFFFF));

	public GlassModule() {
		super("glass", "Glass", "You and others made of see-through glass", Category.VISUALS);
	}

	public boolean applies(Entity e) {
		if (!isEnabled() || !(e instanceof Player)) return false;
		return e == Minecraft.getInstance().player ? self.on() : others.on();
	}
}
