package dev.quiqzy.client.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.misc.SoundsModule;

/**
 * Eigene Client-Klänge (nur für dich), direkt in Java erzeugt – kein Browser, keine Minecraft-Sounds.
 * Weiche Sinus/Dreieck-Töne mit sanfter Hüllkurve und kurzem Nachhall für ein "premium" Gefühl.
 * Gespielt wird auf einem Hintergrund-Thread, damit das Spiel nie hängt.
 */
public final class UiSounds {
	private static final float RATE = 44100f;
	private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
	private static final ExecutorService EXEC = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "ZERO-Sounds");
		t.setDaemon(true);
		return t;
	});
	/** Fertig berechnete Töne (bei Lautstärke 1.0). */
	private static final Map<String, short[]> CACHE = new HashMap<>();
	private static volatile boolean broken;

	private UiSounds() {
	}

	/** Modul an/aus. */
	public static void toggle(boolean on) {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.toggles.on()) return;
		route(on ? "on" : "off");
	}

	/** GUI geöffnet / geschlossen. */
	public static void gui(boolean open) {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.gui.on()) return;
		route(open ? "open" : "close");
	}

	/** Kleiner Klick. */
	public static void click() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.clicks.on()) return;
		route("click");
	}

	/** Etwas hinzugefügt. */
	public static void added() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.clicks.on()) return;
		route("add");
	}

	/** Etwas entfernt. */
	public static void removed() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled() || !s.clicks.on()) return;
		route("remove");
	}

	/** Creamy Keys: mechanischer Tastenklang (eigene Lautstärke, unabhängig vom Sounds-Modul). */
	public static void key(boolean mouse) {
		var k = ModuleManager.KEYSTROKES;
		if (!k.isEnabled() || !k.keySounds.on() || broken) return;
		float vol = k.keyVolume.get() / 100f;
		if (vol <= 0f) return;
		String name = mouse ? "mouse" : "key" + (int) (Math.random() * 3);
		EXEC.execute(() -> play(name, vol));
	}

	/** Treffer (Hit Effects). */
	public static void hit(boolean crit) {
		route(crit ? "crit" : "hit");
	}

	/** Kill (Hit Effects). */
	public static void kill() {
		route("kill");
	}

	/** Neuer Fund (z. B. Sus Chunk). */
	public static void found() {
		SoundsModule s = ModuleManager.SOUNDS;
		if (!s.isEnabled()) return;
		route("found");
	}

	private static void route(String name) {
		float vol = ModuleManager.SOUNDS.volume.get() / 100f;
		if (vol <= 0f || broken) return;
		EXEC.execute(() -> play(name, vol));
	}

	private static void play(String name, float vol) {
		short[] base;
		synchronized (CACHE) {
			base = CACHE.computeIfAbsent(name, UiSounds::synth);
		}
		if (base == null) return;
		// Lautstärke anwenden (leicht kurvig, damit leise Stufen hörbar bleiben)
		float g = vol * vol * 0.85f + vol * 0.15f;
		byte[] pcm = new byte[base.length * 2];
		for (int i = 0; i < base.length; i++) {
			int v = Math.round(base[i] * g);
			pcm[i * 2] = (byte) v;
			pcm[i * 2 + 1] = (byte) (v >> 8);
		}
		try {
			Clip clip = AudioSystem.getClip();
			clip.addLineListener(e -> {
				if (e.getType() == LineEvent.Type.STOP) clip.close();
			});
			clip.open(FORMAT, pcm, 0, pcm.length);
			clip.start();
		} catch (Throwable t) {
			broken = true; // kein Audiogerät o. ä. -> nicht weiter versuchen
			QuiqzyClient.LOGGER.warn("ZERO sounds disabled: {}", t.toString());
		}
	}

	// ------------------------------------------------------------------ Synth

	/** Ein Ton: Frequenz (optional gleitend), Dauer, Startzeit, Lautstärke, Wellenform. */
	private record Note(float freq, float to, float dur, float at, float gain, int wave) {
	}

	private static final int SINE = 0, TRI = 1, SOFT = 2;

	private static short[] synth(String name) {
		Note[] notes = switch (name) {
			case "on" -> new Note[]{
					new Note(659f, 659f, 0.11f, 0.00f, 0.42f, TRI),
					new Note(988f, 988f, 0.16f, 0.06f, 0.36f, SINE)};
			case "off" -> new Note[]{
					new Note(587f, 392f, 0.16f, 0.00f, 0.40f, TRI)};
			case "click" -> new Note[]{
					new Note(2100f, 1500f, 0.035f, 0.00f, 0.22f, SOFT)};
			case "add" -> new Note[]{
					new Note(784f, 1175f, 0.10f, 0.00f, 0.36f, TRI)};
			case "remove" -> new Note[]{
					new Note(523f, 311f, 0.11f, 0.00f, 0.34f, SINE)};
			case "open" -> new Note[]{
					new Note(392f, 392f, 0.18f, 0.00f, 0.28f, SINE),
					new Note(587f, 587f, 0.20f, 0.06f, 0.26f, SINE),
					new Note(784f, 784f, 0.24f, 0.12f, 0.18f, SINE)};
			case "close" -> new Note[]{
					new Note(698f, 392f, 0.18f, 0.00f, 0.28f, SINE)};
			case "key0", "key1", "key2" -> new Note[]{
					// dumpfer "Thock" + kurzer heller Anschlag; drei leicht verschiedene Varianten
					new Note(name.equals("key0") ? 150f : name.equals("key1") ? 165f : 138f, 85f, 0.05f, 0.00f, 0.55f, SINE),
					new Note(name.equals("key0") ? 3100f : name.equals("key1") ? 3400f : 2900f, 2200f, 0.012f, 0.00f, 0.10f, SOFT)};
			case "mouse" -> new Note[]{
					new Note(2600f, 1900f, 0.014f, 0.00f, 0.14f, SOFT),
					new Note(210f, 120f, 0.03f, 0.00f, 0.25f, SINE)};
			case "hit" -> new Note[]{
					new Note(180f, 90f, 0.07f, 0.00f, 0.55f, SINE),
					new Note(2400f, 1800f, 0.03f, 0.00f, 0.12f, SOFT)};
			case "crit" -> new Note[]{
					new Note(200f, 80f, 0.09f, 0.00f, 0.6f, SINE),
					new Note(1568f, 1568f, 0.10f, 0.01f, 0.22f, TRI),
					new Note(2349f, 2349f, 0.16f, 0.05f, 0.16f, SINE)};
			case "kill" -> new Note[]{
					new Note(130f, 60f, 0.22f, 0.00f, 0.6f, SINE),
					new Note(784f, 784f, 0.18f, 0.04f, 0.26f, TRI),
					new Note(1175f, 1175f, 0.20f, 0.12f, 0.24f, TRI),
					new Note(1568f, 1568f, 0.34f, 0.20f, 0.22f, SINE)};
			case "found" -> new Note[]{
					new Note(784f, 784f, 0.14f, 0.00f, 0.34f, TRI),
					new Note(988f, 988f, 0.14f, 0.09f, 0.32f, TRI),
					new Note(1319f, 1319f, 0.26f, 0.18f, 0.30f, SINE)};
			default -> null;
		};
		if (notes == null) return null;

		float end = 0f;
		for (Note n : notes) end = Math.max(end, n.at + n.dur);
		float tail = 0.12f; // Nachhall
		int len = (int) ((end + tail) * RATE);
		float[] mix = new float[len];

		for (Note n : notes) {
			int start = (int) (n.at * RATE);
			int count = (int) (n.dur * RATE);
			double phase = 0;
			for (int i = 0; i < count && start + i < len; i++) {
				float t = i / (float) count;
				// exponentielles Gleiten der Frequenz
				double f = n.freq * Math.pow(n.to / n.freq, t);
				phase += 2 * Math.PI * f / RATE;
				double w = switch (n.wave) {
					case TRI -> 2.0 / Math.PI * Math.asin(Math.sin(phase));
					case SOFT -> Math.sin(phase) * 0.7 + Math.sin(phase * 2.0) * 0.3;
					default -> Math.sin(phase);
				};
				// Hüllkurve: 6 ms weicher Anstieg, dann exponentielles Ausklingen
				float attack = Math.min(1f, i / (RATE * 0.006f));
				float decay = (float) Math.exp(-4.2 * t);
				mix[start + i] += (float) (w * n.gain * attack * decay);
			}
		}

		// Kurzer, leiser Nachhall (zwei Echos) für Tiefe
		int d1 = (int) (0.045f * RATE);
		int d2 = (int) (0.083f * RATE);
		for (int i = len - 1; i >= 0; i--) {
			if (i - d1 >= 0) mix[i] += mix[i - d1] * 0.18f;
			if (i - d2 >= 0) mix[i] += mix[i - d2] * 0.10f;
		}

		// Sanftes Ausblenden am Ende + Soft-Clip
		int fade = (int) (0.02f * RATE);
		short[] out = new short[len];
		for (int i = 0; i < len; i++) {
			float v = mix[i];
			if (i > len - fade) v *= (len - i) / (float) fade;
			v = (float) Math.tanh(v * 1.1);
			out[i] = (short) Math.round(v * 30000f);
		}
		return out;
	}
}
