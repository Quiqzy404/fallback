package dev.quiqzy.client.module.misc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Text Replace / Streamer Mode: ersetzt Text im Scoreboard (nur bei dir sichtbar – Server und andere sehen die echten Werte).
 * Regeln per Chat: /zreplace add <alt> => <neu>, /zreplace list, /zreplace remove <nr>, /zreplace clear
 */
public final class TextReplaceModule extends Module {
	public final Setting.Bool scoreboard = add(new Setting.Bool("scoreboard", "Scoreboard", true));
	public final Setting.Bool hideName = add(new Setting.Bool("hide_name", "Hide My Name", false));
	public final Setting.Strings rules = add(new Setting.Strings("rules", "Rules"));

	public static final String SEP = "\u0000";

	public TextReplaceModule() {
		super("text_replace", "Text Replace", "Change scoreboard text (only you see it) - /zreplace", Category.MISC);
	}

	public record Rule(String from, String to) {
	}

	public List<Rule> list() {
		List<Rule> out = new ArrayList<>();
		for (String r : rules.get()) {
			int i = r.indexOf(SEP);
			if (i > 0) out.add(new Rule(r.substring(0, i), r.substring(i + 1)));
		}
		if (hideName.on()) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.getUser() != null) out.add(new Rule(mc.getUser().getName(), "You"));
		}
		return out;
	}

	public void addRule(String from, String to) {
		List<String> l = new ArrayList<>(rules.get());
		l.add(from + SEP + to);
		rules.set(l);
	}

	/** Regel für genau diesen Originaltext (oder null). */
	public String ruleFor(String from) {
		for (String r : rules.get()) {
			int i = r.indexOf(SEP);
			if (i > 0 && r.substring(0, i).equals(from)) return r.substring(i + 1);
		}
		return null;
	}

	/** Regel setzen/ersetzen; leer oder gleich dem Original = Regel löschen. */
	public void setRule(String from, String to) {
		List<String> l = new ArrayList<>();
		for (String r : rules.get()) {
			int i = r.indexOf(SEP);
			if (!(i > 0 && r.substring(0, i).equals(from))) l.add(r);
		}
		if (to != null && !to.isEmpty() && !to.equals(from)) l.add(from + SEP + to);
		rules.set(l);
	}

	/** Eine Zeile des aktuellen Server-Scoreboards: Originaltext (mit Farben) + Zahl. */
	public record Line(Component text, String plain, String score) {
	}

	/** Titel + Zeilen der Scoreboard-Seitenleiste, so wie Minecraft sie anzeigt (ohne Ersetzung). */
	public static List<Line> serverLines(net.minecraft.client.Minecraft mc, Component[] titleOut) {
		List<Line> out = new ArrayList<>();
		if (mc.level == null) return out;
		var sb = mc.level.getScoreboard();
		var obj = sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
		if (obj == null) return out;
		titleOut[0] = obj.getDisplayName();
		var fmt = obj.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
		sb.listPlayerScores(obj).stream()
				.filter(e -> !e.isHidden())
				.sorted(java.util.Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
						.thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
				.limit(15)
				.forEach(e -> {
					var team = sb.getPlayersTeam(e.owner());
					Component name = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team, e.ownerName());
					out.add(new Line(name, name.getString(), e.formatValue(fmt).getString()));
				});
		return out;
	}

	public boolean removeRule(int index) {
		List<String> l = new ArrayList<>(rules.get());
		if (index < 0 || index >= l.size()) return false;
		l.remove(index);
		rules.set(l);
		return true;
	}

	/** Text ersetzen, Farben/Formatierung bleiben erhalten. */
	public Component apply(Component c) {
		if (!isEnabled() || c == null) return c;
		List<Rule> rs = list();
		if (rs.isEmpty()) return c;
		String plain = c.getString();
		boolean any = false;
		for (Rule r : rs) if (plain.contains(r.from())) { any = true; break; }
		if (!any) return c;
		// Teil für Teil ersetzen (jedes Stück behält seinen Stil)
		MutableComponent out = Component.empty();
		boolean[] changed = {false};
		c.visit((Style style, String text) -> {
			String t = text;
			for (Rule r : rs) {
				if (t.contains(r.from())) {
					t = t.replace(r.from(), r.to().replace('&', '\u00a7'));
					changed[0] = true;
				}
			}
			out.append(Component.literal(t).withStyle(style));
			return java.util.Optional.empty();
		}, Style.EMPTY);
		if (changed[0]) return out;
		// Treffer geht über mehrere farbige Stücke -> als ein Stück mit dem ersten Stil
		String t = plain;
		for (Rule r : rs) t = t.replace(r.from(), r.to().replace('&', '\u00a7'));
		Style first = c.getStyle();
		return Component.literal(t).withStyle(first);
	}
}
