package dev.quiqzy.client.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Song, der gerade läuft (Windows-Mediensteuerung über ein kleines PowerShell-Skript) + synchronisierte Lyrics (lrclib.net).
 * Kein Spotify-Login nötig. Nur Windows.
 */
public final class MediaInfo {
	private MediaInfo() {
	}

	public record Track(String title, String artist, String album, long posMs, long durMs, boolean playing, String app, String cover, long at) {
		/** Aktuelle Position, zwischen den Updates weitergezählt. */
		public long position() {
			return playing ? Math.min(durMs > 0 ? durMs : Long.MAX_VALUE, posMs + (System.currentTimeMillis() - at)) : posMs;
		}
	}

	public record Line(long timeMs, String text) {
	}

	public static volatile Track track;
	public static volatile List<Line> lyrics = List.of();
	public static volatile String lyricsState = "";
	private static String lyricsKey = "";
	private static Process process;
	private static Thread reader;
	private static boolean onlySpotify = true;
	private static Path dir;

	private static Path dir() throws Exception {
		if (dir == null) {
			dir = Path.of(System.getProperty("java.io.tmpdir"), "zero-client-media");
			Files.createDirectories(dir);
			for (String f : new String[]{"media_watch.ps1", "media_control.ps1"}) {
				try (InputStream in = MediaInfo.class.getResourceAsStream("/assets/quiqzy/media/" + f)) {
					if (in != null) Files.write(dir.resolve(f), in.readAllBytes());
				}
			}
		}
		return dir;
	}

	public static synchronized void start(boolean spotifyOnly) {
		if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
		if (process != null && process.isAlive() && spotifyOnly == onlySpotify) return;
		stop();
		onlySpotify = spotifyOnly;
		try {
			Path d = dir();
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
					"-WindowStyle", "Hidden", "-File", d.resolve("media_watch.ps1").toString(), "-CoverDir", d.toString(), "-OnlySpotify", spotifyOnly ? "1" : "0");
			pb.redirectErrorStream(true);
			process = pb.start();
			Process proc = process;
			reader = new Thread(() -> read(proc), "ZERO-Media");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception e) {
			process = null;
		}
	}

	public static synchronized void stop() {
		if (process != null) process.destroy();
		process = null;
		track = null;
	}

	private static void read(Process proc) {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.equals("NONE")) {
					track = null;
					continue;
				}
				String[] p = line.split("\t", -1);
				if (p.length < 8) continue;
				try {
					Track t = new Track(p[0].strip(), p[1].strip(), p[2].strip(), Long.parseLong(p[3]), Long.parseLong(p[4]),
							p[5].equals("Playing"), p[6], p[7].isBlank() ? null : p[7], System.currentTimeMillis());
					if (t.title().isEmpty()) continue;
					track = t;
					String key = t.artist() + "|" + t.title();
					if (!key.equals(lyricsKey)) {
						lyricsKey = key;
						fetchLyrics(t);
					}
				} catch (NumberFormatException ignored) {
				}
			}
		} catch (Exception ignored) {
		}
	}

	/** Selbsttest: einen Song vortäuschen (Helfer-Prozess wird gestoppt, damit er nicht überschreibt). */
	public static void injectTest(String title, String artist, long pos, long dur) {
		stop();
		Track t = new Track(title, artist, "", pos, dur, true, "test", null, System.currentTimeMillis());
		track = t;
		lyricsKey = artist + "|" + title;
		fetchLyrics(t);
	}

	public static void control(String action) {
		CompletableFuture.runAsync(() -> {
			try {
				Path d = dir();
				new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
						"-File", d.resolve("media_control.ps1").toString(), "-Action", action, "-OnlySpotify", onlySpotify ? "1" : "0")
						.redirectErrorStream(true).start().waitFor();
			} catch (Exception ignored) {
			}
		});
	}

	// ------------------------------------------------------------------ Lyrics (lrclib.net, frei, ohne Schlüssel)

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

	private static void fetchLyrics(Track t) {
		lyrics = List.of();
		lyricsState = "Loading lyrics...";
		CompletableFuture.runAsync(() -> {
			try {
				String artist = t.artist().split(",")[0].strip();
				String url = "https://lrclib.net/api/get?track_name=" + enc(t.title()) + "&artist_name=" + enc(artist)
						+ (t.durMs() > 0 ? "&duration=" + Math.round(t.durMs() / 1000.0) : "");
				String synced = get(url);
				if (synced == null) {
					// unscharfe Suche als Ersatz
					String body = request("https://lrclib.net/api/search?q=" + enc(artist + " " + t.title()));
					if (body != null) {
						var arr = JsonParser.parseString(body).getAsJsonArray();
						for (var el : arr) {
							JsonObject o = el.getAsJsonObject();
							if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
								synced = o.get("syncedLyrics").getAsString();
								break;
							}
						}
					}
				}
				if (!(t.artist() + "|" + t.title()).equals(lyricsKey)) return; // Song inzwischen gewechselt
				if (synced == null || synced.isBlank()) {
					lyrics = List.of();
					lyricsState = "No synced lyrics found";
					return;
				}
				lyrics = parse(synced);
				lyricsState = "";
			} catch (Exception e) {
				lyricsState = "Lyrics unavailable";
			}
		});
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String request(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "ZERO Client (Minecraft mod)").GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return res.statusCode() == 200 ? res.body() : null;
	}

	private static String get(String url) throws Exception {
		String body = request(url);
		if (body == null) return null;
		JsonObject o = JsonParser.parseString(body).getAsJsonObject();
		return o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() ? o.get("syncedLyrics").getAsString() : null;
	}

	/** "[mm:ss.xx] Text" -> Zeilen mit Zeitstempel. */
	static List<Line> parse(String lrc) {
		List<Line> out = new ArrayList<>();
		for (String raw : lrc.split("\n")) {
			String l = raw.strip();
			int close = l.indexOf(']');
			if (!l.startsWith("[") || close < 0) continue;
			String ts = l.substring(1, close);
			String text = l.substring(close + 1).strip();
			try {
				String[] mp = ts.split(":");
				long ms = Long.parseLong(mp[0]) * 60000L + Math.round(Double.parseDouble(mp[1]) * 1000);
				out.add(new Line(ms, text.isEmpty() ? "..." : text));
			} catch (Exception ignored) {
			}
		}
		out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
		return out;
	}

	/** Index der aktuellen Zeile (oder -1). */
	public static int currentLine(long posMs) {
		List<Line> l = lyrics;
		int idx = -1;
		for (int i = 0; i < l.size(); i++) {
			if (l.get(i).timeMs() <= posMs) idx = i;
			else break;
		}
		return idx;
	}
}
