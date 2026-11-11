package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Status Bar: schlanke Leiste oben in der Mitte – RAM, Uhrzeit, Server, FPS, Ping. */
public final class StatusBarModule extends Module {
	public final Setting.Bool memory = add(new Setting.Bool("memory", "Memory", true));
	public final Setting.Bool clock = add(new Setting.Bool("clock", "Clock", true));
	public final Setting.Bool server = add(new Setting.Bool("server", "Server", true));
	public final Setting.Bool fps = add(new Setting.Bool("fps", "FPS", true));
	public final Setting.Bool ping = add(new Setting.Bool("ping", "Ping", true));

	public StatusBarModule() {
		super("status_bar", "Status Bar", "Clean top bar with memory, time, server, FPS and ping", Category.HUD);
	}
}
