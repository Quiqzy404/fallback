package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Chat Tweaks: Uhrzeit vor jeder Nachricht. */
public final class ChatTweaksModule extends Module {
	public final Setting.Bool seconds = add(new Setting.Bool("seconds", "Show Seconds", false));

	public ChatTweaksModule() {
		super("chat_tweaks", "Chat Timestamps", "Adds the time in front of chat messages", Category.MISC);
	}
}
