package dev.quiqzy.client.module;

/** Eine Einstellung eines Moduls (Schalter oder Zahl-Slider). */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	protected T value;

	protected Setting(String id, String name, T value) {
		this.id = id;
		this.name = name;
		this.value = value;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	/** Setzt die Einstellung auf ihren Standardwert zurück. */
	public void reset() {
	}

	/** Schalter (an/aus). */
	public static final class Bool extends Setting<Boolean> {
		private final boolean def;

		public Bool(String id, String name, boolean value) {
			super(id, name, value);
			this.def = value;
		}

		@Override
		public void reset() {
			value = def;
		}

		public boolean on() {
			return value;
		}

		public void toggle() {
			value = !value;
		}
	}

	/** Auswahl aus festen Optionen (im HTML-GUI ein Dropdown). */
	public static final class Choice extends Setting<String> {
		private final java.util.List<String> options;

		private final String def;

		public Choice(String id, String name, String value, String... options) {
			super(id, name, value);
			this.options = java.util.List.of(options);
			this.def = value;
		}

		@Override
		public void reset() {
			this.value = def;
		}

		public java.util.List<String> options() {
			return options;
		}

		@Override
		public void set(String v) {
			if (options.contains(v)) this.value = v;
		}

		/** Nächste Option (für das eingebaute GUI). */
		public void cycle() {
			int i = options.indexOf(value);
			value = options.get((i + 1) % options.size());
		}
	}

	/** Frei wählbare Liste von Block-IDs (z. B. "minecraft:diamond_ore"). Im HTML-GUI mit Such-Menü. */
	public static final class Blocks extends Setting<java.util.List<String>> {
		private int version;
		private final java.util.List<String> def;

		public Blocks(String id, String name, String... defaults) {
			super(id, name, new java.util.ArrayList<>(java.util.List.of(defaults)));
			this.def = java.util.List.of(defaults);
		}

		@Override
		public void reset() {
			set(new java.util.ArrayList<>(def));
		}

		@Override
		public void set(java.util.List<String> v) {
			this.value = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(v));
			version++;
		}

		public boolean contains(String blockId) {
			return value.contains(blockId);
		}

		public void add(String blockId) {
			if (!value.contains(blockId)) {
				value.add(blockId);
				version++;
			}
		}

		public void remove(String blockId) {
			if (value.remove(blockId)) version++;
		}

		/** Zählt bei jeder Änderung hoch (damit Module merken, dass neu gescannt werden muss). */
		public int version() {
			return version;
		}
	}

	/** Frei wählbare Liste von Item-IDs (z. B. "minecraft:diamond_axe"). Im HTML-GUI mit Such-Menü. */
	public static final class Items extends Setting<java.util.List<String>> {
		private int version;
		private final java.util.List<String> def;

		public Items(String id, String name, String... defaults) {
			super(id, name, new java.util.ArrayList<>(java.util.List.of(defaults)));
			this.def = java.util.List.of(defaults);
		}

		@Override
		public void reset() {
			set(new java.util.ArrayList<>(def));
		}

		@Override
		public void set(java.util.List<String> v) {
			this.value = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(v));
			version++;
		}

		public boolean contains(String itemId) {
			return value.contains(itemId);
		}

		public void add(String itemId) {
			if (!value.contains(itemId)) {
				value.add(itemId);
				version++;
			}
		}

		public void remove(String itemId) {
			if (value.remove(itemId)) version++;
		}

		/** Zählt bei jeder Änderung hoch (damit Module merken, dass sie neu bauen müssen). */
		public int version() {
			return version;
		}
	}

	/** Eigene Farbe pro ID (z. B. Block-ID -> 0xRRGGBB). Wird nicht als eigene Zeile im GUI gezeigt. */
	public static final class ColorMap extends Setting<java.util.Map<String, Integer>> {
		private int version;

		public ColorMap(String id, String name) {
			super(id, name, new java.util.LinkedHashMap<>());
		}

		@Override
		public void reset() {
			value = new java.util.LinkedHashMap<>();
			version++;
		}

		@Override
		public void set(java.util.Map<String, Integer> v) {
			this.value = new java.util.LinkedHashMap<>(v);
			version++;
		}

		/** Eigene Farbe oder null. */
		public Integer color(String id) {
			return value.get(id);
		}

		public void put(String id, int rgb) {
			value.put(id, rgb & 0xFFFFFF);
			version++;
		}

		public void remove(String id) {
			if (value.remove(id) != null) version++;
		}

		public int version() {
			return version;
		}
	}

	/** Freie Textliste (z. B. Ersetzungs-Regeln). Wird im GUI nicht als eigene Zeile gezeigt. */
	public static final class Strings extends Setting<java.util.List<String>> {
		public Strings(String id, String name) {
			super(id, name, new java.util.ArrayList<>());
		}

		@Override
		public void reset() {
			value = new java.util.ArrayList<>();
		}

		@Override
		public void set(java.util.List<String> v) {
			this.value = new java.util.ArrayList<>(v);
		}
	}

	/** Liste von Wegpunkten (Name, Position, Dimension). Im HTML-GUI mit "Add here" und ×. */
	public static final class Waypoints extends Setting<java.util.List<Waypoints.Point>> {
		public record Point(String name, int x, int y, int z, String dim) {
		}

		public Waypoints(String id, String name) {
			super(id, name, new java.util.ArrayList<>());
		}

		@Override
		public void reset() {
			value = new java.util.ArrayList<>();
		}

		@Override
		public void set(java.util.List<Point> v) {
			this.value = new java.util.ArrayList<>(v);
		}

		public void add(Point p) {
			value.add(p);
		}

		public void remove(int index) {
			if (index >= 0 && index < value.size()) value.remove(index);
		}
	}

	/** Farbe 0xRRGGBB (im HTML-GUI ein Farbfeld mit Palette + Hex-Eingabe). */
	public static final class Color extends Setting<Integer> {
		private final int def;

		public Color(String id, String name, int rgb) {
			super(id, name, rgb & 0xFFFFFF);
			this.def = rgb & 0xFFFFFF;
		}

		@Override
		public void reset() {
			this.value = def;
		}

		@Override
		public void set(Integer v) {
			this.value = v & 0xFFFFFF;
		}

		public String hex() {
			return String.format("#%06X", value);
		}

		/** "#RRGGBB" oder "RRGGBB" setzen, ungültig = ignorieren. */
		public void setHex(String hex) {
			if (hex == null) return;
			String h = hex.trim();
			if (h.startsWith("#")) h = h.substring(1);
			if (h.length() != 6) return;
			try {
				set(Integer.parseInt(h, 16));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	/** Ganzzahl mit Min/Max (wird im GUI als Slider gezeigt). */
	public static final class Int extends Setting<Integer> {
		private final int min;
		private final int max;
		private final String unit;

		public Int(String id, String name, int value, int min, int max) {
			this(id, name, value, min, max, "");
		}

		private final int def;

		public Int(String id, String name, int value, int min, int max, String unit) {
			super(id, name, value);
			this.min = min;
			this.max = max;
			this.unit = unit;
			this.def = value;
		}

		@Override
		public void reset() {
			set(def);
		}

		/** Einheit hinter dem Wert im HTML-GUI (z. B. "Ch"). */
		public String unit() {
			return unit;
		}

		public int min() {
			return min;
		}

		public int max() {
			return max;
		}

		@Override
		public void set(Integer v) {
			this.value = Math.max(min, Math.min(max, v));
		}

		/** 0..1 Position auf dem Slider. */
		public float progress() {
			return (value - min) / (float) (max - min);
		}

		public void setProgress(float p) {
			p = Math.max(0f, Math.min(1f, p));
			set(Math.round(min + p * (max - min)));
		}
	}
}
