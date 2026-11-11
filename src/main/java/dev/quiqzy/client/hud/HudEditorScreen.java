package dev.quiqzy.client.hud;

import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.util.UiSounds;

/**
 * HUD-Editor: alle HUD-Elemente per Drag & Drop verschieben.
 * Linksklick + ziehen = verschieben, Rechtsklick = zurücksetzen, Esc = fertig (wird gespeichert).
 */
public final class HudEditorScreen extends Screen {
	private String dragging;
	private float grabX, grabY, startDx, startDy;
	private String hovered;
	/** Aktive Hilfslinien beim Ziehen: {x} senkrecht, {y} waagerecht (NaN = keine). */
	private float guideX = Float.NaN, guideY = Float.NaN;
	private static final float SNAP = 6f;

	public HudEditorScreen() {
		super(Component.literal("HUD Editor"));
	}

	/** Maus (GUI-Koordinaten) -> virtuelle HUD-Koordinaten. */
	private static float v(double gui) {
		return (float) gui * ZeroHud.lastK / ZeroHud.lastScale;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// kein Abdunkeln – das HUD soll sichtbar bleiben
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float s = ZeroHud.lastScale / ZeroHud.lastK;
		float mx = v(mouseX), my = v(mouseY);
		hovered = null;
		g.pose().pushMatrix();
		g.pose().scale(s);
		float vw = this.width / s, vh = this.height / s;
		// leichtes Raster
		for (float x = 0; x < vw; x += 40f) ZeroRender.rect(g, x, 0, x + 0.4f, vh, 0f, 0x10FFFFFF);
		for (float y = 0; y < vh; y += 40f) ZeroRender.rect(g, 0, y, vw, y + 0.4f, 0f, 0x10FFFFFF);
		for (Map.Entry<String, float[]> e : ZeroHud.BOUNDS.entrySet()) {
			float[] b = e.getValue();
			boolean hot = dragging != null ? dragging.equals(e.getKey()) : (mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3]);
			if (hot && dragging == null) hovered = e.getKey();
			int c = hot ? 0xFFFFB26E : 0x80FFFFFF;
			ZeroRender.rect(g, b[0] - 2f, b[1] - 2f, b[2] + 2f, b[3] + 2f, 4f, hot ? 0x26F0782C : 0x0EFFFFFF);
			ZeroRender.stroke(g, b[0] - 2f, b[1] - 2f, b[2] + 2f, b[3] + 2f, 4f, hot ? 1f : 0.6f, c);
			String label = e.getKey().toUpperCase(java.util.Locale.ROOT);
			ZeroRender.text(g, ZeroFont.BOLD, label, b[0], b[1] - 9f, 5.6f, c);
		}
		// Hilfslinien beim Andocken
		if (!Float.isNaN(guideX)) ZeroRender.rect(g, guideX - 0.4f, 0, guideX + 0.4f, vh, 0f, 0xC0FFB26E);
		if (!Float.isNaN(guideY)) ZeroRender.rect(g, 0, guideY - 0.4f, vw, guideY + 0.4f, 0f, 0xC0FFB26E);
		// Hinweis oben
		String tip = "Drag to move (snaps to edges)  ·  Right-click = reset  ·  Esc = done";
		float tw = ZeroFont.BOLD.width(tip, 7.4f, 0f);
		ZeroRender.rect(g, vw / 2f - tw / 2f - 12f, 30f, vw / 2f + tw / 2f + 12f, 47f, 8.5f, 0xE0120E0C);
		ZeroRender.stroke(g, vw / 2f - tw / 2f - 12f, 30f, vw / 2f + tw / 2f + 12f, 47f, 8.5f, 0.6f, 0x80FFB26E);
		ZeroRender.textCentered(g, ZeroFont.BOLD, tip, vw / 2f, 34.5f, 7.4f, 0xFFF4EEE8);
		g.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
		float mx = v(event.x()), my = v(event.y());
		for (Map.Entry<String, float[]> e : ZeroHud.BOUNDS.entrySet()) {
			float[] b = e.getValue();
			if (mx >= b[0] && mx <= b[2] && my >= b[1] && my <= b[3]) {
				if (event.button() == 1) {
					ModuleManager.ACCENT.setOffset(e.getKey(), 0f, 0f);
					QuiqzyConfig.save();
					UiSounds.removed();
					return true;
				}
				if (event.button() == 0) {
					dragging = e.getKey();
					float[] off = ModuleManager.ACCENT.offset(dragging);
					startDx = off[0];
					startDy = off[1];
					grabX = mx;
					grabY = my;
					UiSounds.click();
					return true;
				}
			}
		}
		return super.mouseClicked(event, dbl);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			float nx = startDx + v(event.x()) - grabX;
			float ny = startDy + v(event.y()) - grabY;
			float[] cur = ZeroHud.BOUNDS.get(dragging);
			guideX = Float.NaN;
			guideY = Float.NaN;
			if (cur != null) {
				float[] off = ModuleManager.ACCENT.offset(dragging);
				// Rahmen ohne Verschiebung + neue Verschiebung
				float bx1 = cur[0] - off[0] + nx, bx2 = cur[2] - off[0] + nx;
				float by1 = cur[1] - off[1] + ny, by2 = cur[3] - off[1] + ny;
				float s = ZeroHud.lastScale / ZeroHud.lastK;
				float vw = this.width / s, vh = this.height / s;
				java.util.List<Float> xs = new java.util.ArrayList<>(java.util.List.of(8f, vw / 2f, vw - 8f));
				java.util.List<Float> ys = new java.util.ArrayList<>(java.util.List.of(8f, vh / 2f, vh - 8f));
				for (Map.Entry<String, float[]> e : ZeroHud.BOUNDS.entrySet()) {
					if (e.getKey().equals(dragging)) continue;
					float[] b = e.getValue();
					xs.add(b[0]); xs.add(b[2]);
					ys.add(b[1]); ys.add(b[3]);
				}
				float bestX = SNAP, bestY = SNAP;
				float[] myX = {bx1, (bx1 + bx2) / 2f, bx2};
				float[] myY = {by1, (by1 + by2) / 2f, by2};
				float shiftX = 0f, shiftY = 0f;
				for (float t : xs) for (float m : myX) if (Math.abs(t - m) < bestX) { bestX = Math.abs(t - m); shiftX = t - m; guideX = t; }
				for (float t : ys) for (float m : myY) if (Math.abs(t - m) < bestY) { bestY = Math.abs(t - m); shiftY = t - m; guideY = t; }
				nx += shiftX;
				ny += shiftY;
			}
			ModuleManager.ACCENT.setOffset(dragging, nx, ny);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			guideX = Float.NaN;
			guideY = Float.NaN;
			QuiqzyConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 256) {
			QuiqzyConfig.save();
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
