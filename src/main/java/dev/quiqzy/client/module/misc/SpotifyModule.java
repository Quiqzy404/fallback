package dev.quiqzy.client.module.misc;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.MediaInfo;

/** Spotify HUD: Song, Künstler, Cover und Fortschritt; optional synchronisierte Lyrics im Spiel. Nur Windows. */
public final class SpotifyModule extends Module {
	public final Setting.Bool cover = add(new Setting.Bool("cover", "Album Cover", true));
	public final Setting.Bool lyrics = add(new Setting.Bool("lyrics", "Show Lyrics", true));
	public final Setting.Choice lyricsPos = add(new Setting.Choice("lyrics_pos", "Lyrics Position", "Above Hotbar", "Above Hotbar", "Top", "Under Widget"));
	public final Setting.Int lyricsSize = add(new Setting.Int("lyrics_size", "Lyrics Size", 100, 60, 200, " %"));
	public final Setting.Int offset = add(new Setting.Int("offset", "Lyrics Offset", 0, -3000, 3000, " ms"));
	public final Setting.Bool hidePaused = add(new Setting.Bool("hide_paused", "Hide When Paused", false));
	public final Setting.Bool spotifyOnly = add(new Setting.Bool("spotify_only", "Spotify Only", true));

	public SpotifyModule() {
		super("spotify", "Spotify", "Now playing + live lyrics in game", Category.HUD);
	}

	@Override
	protected void onEnable() {
		MediaInfo.start(spotifyOnly.on());
	}

	@Override
	protected void onDisable() {
		MediaInfo.stop();
	}

	@Override
	public void onTick(Minecraft mc) {
		if (MediaInfo.track != null && "test".equals(MediaInfo.track.app())) return;
		MediaInfo.start(spotifyOnly.on()); // startet neu, falls der Helfer beendet wurde oder die Option sich ändert
	}
}
