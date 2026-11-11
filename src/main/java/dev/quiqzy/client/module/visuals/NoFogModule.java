package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** NoFog: entfernt Wetter-/Umgebungsnebel, optional auch den Sichtweiten-Nebel am Rand und Nebel in Wasser/Lava. */
public final class NoFogModule extends Module {
	public final Setting.Bool distance = add(new Setting.Bool("distance", "Remove Distance Fog", true));
	public final Setting.Bool liquids = add(new Setting.Bool("liquids", "Clear Water / Lava", false));

	public NoFogModule() {
		super("no_fog", "No Fog", "Removes fog so you can see further", Category.VISUALS);
	}
}
