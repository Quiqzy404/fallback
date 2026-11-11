package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;

/** NoHurtCam: die Kamera wackelt nicht mehr, wenn du Schaden bekommst. */
public final class NoHurtCamModule extends Module {
	public NoHurtCamModule() {
		super("no_hurt_cam", "No Hurt Cam", "No camera shake when you take damage", Category.VISUALS);
	}
}
