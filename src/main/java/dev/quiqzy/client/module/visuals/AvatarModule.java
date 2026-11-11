package dev.quiqzy.client.module.visuals;

import java.util.Locale;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Avatar: verwandelt dich (nur bei dir sichtbar) in die eigene ZERO-Figur "Rook" –
 * Hoodie, Cargo-Hose, rückwärts getragene Cap, Sonnenbrille, Kette.
 * Volle 3D-Spielerfigur mit allen Vanilla-Animationen, auch die Hand in der Ego-Sicht.
 * Texturen: assets/quiqzy/textures/avatar/rook_<outfit>_<tone>.png (erzeugt mit tools/make_avatar.py).
 */
public final class AvatarModule extends Module {
	/** Rook = eigene Figur, Glass = durchsichtiger Glas-Körper mit leuchtenden Kanten. */
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Glass", "Glass", "Rook"));
	public final Setting.Choice glassColor = add(new Setting.Choice("glass_color", "Glass Colour", "Ice", "Ice", "Clear", "Ember", "Violet", "Mint"));
	public final Setting.Choice glassLevel = add(new Setting.Choice("glass_level", "Glass Opacity", "Light", "Light", "Medium"));
	public final Setting.Choice outfit = add(new Setting.Choice("outfit", "Outfit", "Orange", "Orange", "Midnight", "Ice"));
	public final Setting.Choice tone = add(new Setting.Choice("tone", "Skin Tone", "Medium", "Light", "Medium", "Dark"));
	public final Setting.Bool others = add(new Setting.Bool("others", "Apply to Others", false));

	private String cachedKey = "";
	private ClientAsset.Texture cachedTexture;

	public AvatarModule() {
		super("avatar", "Avatar", "Glass body with glowing outlines, or Rook", Category.VISUALS);
	}

	/** Ersetzt den Skin (Umhang/Elytra bleiben erhalten). */
	public PlayerSkin apply(PlayerSkin original) {
		String key = style.get().equals("Glass")
				? "glass_" + glassColor.get().toLowerCase(Locale.ROOT) + "_" + glassLevel.get().toLowerCase(Locale.ROOT)
				: "rook_" + outfit.get().toLowerCase(Locale.ROOT) + "_" + tone.get().toLowerCase(Locale.ROOT);
		if (!key.equals(cachedKey) || cachedTexture == null) {
			cachedKey = key;
			cachedTexture = new ClientAsset.ResourceTexture(
					Identifier.fromNamespaceAndPath("quiqzy", "avatar/" + key),
					Identifier.fromNamespaceAndPath("quiqzy", "textures/avatar/" + key + ".png"));
		}
		return new PlayerSkin(cachedTexture, original.cape(), original.elytra(), PlayerModelType.WIDE, original.secure());
	}
}
