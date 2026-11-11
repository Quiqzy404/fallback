package dev.quiqzy.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;

/** Basis für alle Module im Quiqzy-GUI. */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	/** GLFW-Taste zum An/Aus-Schalten, -1 = keine. */
	private int keybind = -1;

	protected Module(String id, String name, String description, Category category) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public int keybind() {
		return keybind;
	}

	public void setKeybind(int key) {
		this.keybind = key;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
		dev.quiqzy.client.util.UiSounds.toggle(enabled);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Wird am ANFANG jedes Client-Ticks aufgerufen (vor Minecrafts Tasten-Verarbeitung), solange das Modul an ist. */
	public void onStartTick(Minecraft mc) {
	}

	/** Wird jedes Bild (Frame) aufgerufen, solange das Modul an ist – für flüssige Bewegungen. */
	public void onFrame(Minecraft mc) {
	}

	/** Wird jeden Client-Tick aufgerufen, solange das Modul an ist. */
	public void onTick(Minecraft mc) {
	}

	public enum Category {
		COMBAT("Combat", "⚔"),
		MOVEMENT("Movement", "➜"),
		VISUALS("Visuals", "✦"),
		HUD("HUD", "▦"),
		PLAYER("Player", "☺"),
		WORLD("World", "◈"),
		MISC("Misc", "≡");

		public final String label;
		public final String icon;

		/** ID wie im HTML-Design ("world", "combat", ...). */
		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		Category(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}
}
