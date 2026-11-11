package dev.quiqzy.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import dev.quiqzy.client.QuiqzyClient;

/**
 * Zeichen-Helfer für den eigenen Look.
 * Runde Formen werden in echten Bildschirm-Pixeln mit Kantenglättung gezeichnet
 * (nicht in den groben GUI-Pixeln), dadurch sind die Ecken wirklich rund.
 */
public final class Draw {
	// ---- Farbpalette (ARGB) ----
	public static final int PANEL = 0xF5101017;
	public static final int CARD = 0xFF181722;
	public static final int CARD_HOVER = 0xFF1F1E2B;
	public static final int LINE = 0x1AFFFFFF;
	public static final int TEXT = 0xFFF1EFF8;
	public static final int MUTED = 0xFF9592A8;
	public static final int FAINT = 0xFF56536A;
	public static final int ACCENT = 0xFFB57CFF;
	public static final int ACCENT_2 = 0xFF7C5CFF;
	public static final int TRACK_OFF = 0xFF2C2B38;

	// ---- Schrift (Nunito, siehe assets/quiqzy/font) ----
	private static final FontDescription UI = new FontDescription.Resource(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "ui"));
	private static final FontDescription UI_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "ui_bold"));

	private Draw() {
	}

	// =================================================================== Text

	public static Component ui(String s) {
		return Component.literal(s).withStyle(style -> style.withFont(UI));
	}

	public static Component bold(String s) {
		return Component.literal(s).withStyle(style -> style.withFont(UI_BOLD));
	}

	public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
		g.drawString(font, ui(s), x, y, color, false);
	}

	public static void textBold(GuiGraphics g, Font font, String s, int x, int y, int color) {
		g.drawString(font, bold(s), x, y, color, false);
	}

	public static void textRight(GuiGraphics g, Font font, String s, int right, int y, int color) {
		Component c = ui(s);
		g.drawString(font, c, right - font.width(c), y, color, false);
	}

	public static int width(Font font, String s) {
		return font.width(ui(s));
	}

	/** Kürzt Text mit "…", damit er in maxWidth passt. */
	public static String fit(Font font, String s, int maxWidth) {
		if (width(font, s) <= maxWidth) return s;
		String cut = s;
		while (!cut.isEmpty() && width(font, cut + "…") > maxWidth) cut = cut.substring(0, cut.length() - 1);
		return cut + "…";
	}

	// =================================================================== Runde Formen

	/** Abgerundetes Rechteck mit weichen Kanten. Koordinaten in GUI-Einheiten. */
	public static void round(GuiGraphics g, float x1, float y1, float x2, float y2, float r, int color) {
		if (x2 <= x1 || y2 <= y1 || ((color >>> 24) & 0xFF) == 0) return;

		float s = guiScale();
		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		roundPx(g, x1 * s, y1 * s, x2 * s, y2 * s, r * s, color);
		g.pose().popMatrix();
	}

	/** Kreis (z. B. Schalter-Knopf). */
	public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
		round(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
	}

	/** Runder Rahmen + Füllung. */
	public static void roundOutline(GuiGraphics g, float x1, float y1, float x2, float y2, float r, int border, int fill) {
		round(g, x1, y1, x2, y2, r, border);
		round(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fill);
	}

	/** Weicher Schatten unter einem Fenster. */
	public static void shadow(GuiGraphics g, float x1, float y1, float x2, float y2, float r, float strength) {
		for (int i = 6; i >= 1; i--) {
			int a = Math.round(strength * 14f / i);
			round(g, x1 - i, y1 - i + 2, x2 + i, y2 + i + 2, r + i, (a << 24));
		}
	}

	/** Zeichnet in echten Pixeln. Pro Zeile 4 Unterzeilen -> geglättete Kante. */
	private static void roundPx(GuiGraphics g, float fx1, float fy1, float fx2, float fy2, float r, int color) {
		int x1 = Math.round(fx1);
		int y1 = Math.round(fy1);
		int x2 = Math.round(fx2);
		int y2 = Math.round(fy2);
		r = Math.min(r, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
		int ri = (int) Math.ceil(r);

		if (ri <= 0) {
			g.fill(x1, y1, x2, y2, color);
			return;
		}

		// Mittelteil
		if (y2 - ri > y1 + ri) g.fill(x1, y1 + ri, x2, y2 - ri, color);

		float[] insets = new float[4];
		for (int row = 0; row < ri; row++) {
			// Einrückung für 4 Unterzeilen berechnen
			float minInset = Float.MAX_VALUE;
			float maxInset = 0;
			for (int k = 0; k < 4; k++) {
				float yy = row + (k + 0.5f) / 4f;
				float dy = r - yy;
				float inset = dy <= 0 ? 0 : (dy >= r ? r : r - (float) Math.sqrt(r * r - dy * dy));
				insets[k] = inset;
				minInset = Math.min(minInset, inset);
				maxInset = Math.max(maxInset, inset);
			}

			int solid = (int) Math.ceil(maxInset);
			int edgeStart = (int) Math.floor(minInset);

			int top = y1 + row;
			int bottom = y2 - row - 1;
			if (bottom < top) break;

			// voller Teil der Zeile
			if (x2 - solid > x1 + solid) {
				g.fill(x1 + solid, top, x2 - solid, top + 1, color);
				if (bottom != top) g.fill(x1 + solid, bottom, x2 - solid, bottom + 1, color);
			}

			// Kantenpixel mit Teil-Transparenz
			for (int px = edgeStart; px < solid; px++) {
				float cov = 0;
				for (int k = 0; k < 4; k++) {
					cov += Math.max(0f, Math.min(1f, px + 1 - insets[k]));
				}
				cov /= 4f;
				if (cov <= 0.01f) continue;
				int c = alpha(color, cov);
				g.fill(x1 + px, top, x1 + px + 1, top + 1, c);
				g.fill(x2 - px - 1, top, x2 - px, top + 1, c);
				if (bottom != top) {
					g.fill(x1 + px, bottom, x1 + px + 1, bottom + 1, c);
					g.fill(x2 - px - 1, bottom, x2 - px, bottom + 1, c);
				}
			}
		}
	}

	private static float guiScale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	// =================================================================== Farben / Animation

	/** Farbe mit Faktor auf den Alpha-Kanal (für Fade-In). */
	public static int alpha(int color, float f) {
		int a = (color >>> 24) & 0xFF;
		a = Math.max(0, Math.min(255, Math.round(a * f)));
		return (a << 24) | (color & 0x00FFFFFF);
	}

	public static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24)
				| ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8)
				| (int) (ab + (bb - ab) * t);
	}

	/** Bildrate-unabhängige Annäherung für flüssige Animationen. */
	public static float approach(float current, float target, float speed, float deltaSeconds) {
		float k = 1f - (float) Math.exp(-speed * deltaSeconds);
		return current + (target - current) * k;
	}

	public static boolean inside(double mx, double my, float x1, float y1, float x2, float y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}
}
