package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** X-Ray: leuchtendes Skelett in Spielern (und optional Mobs) – läuft und schwingt mit, sichtbar durch Wände. */
public final class XRayModule extends Module {
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));
	public final Setting.Bool self = add(new Setting.Bool("self", "Yourself (F5)", false));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 48, 8, 128, " m"));

	public XRayModule() {
		super("xray", "X-Ray", "Glowing skeletons inside players", Category.VISUALS);
	}
}
