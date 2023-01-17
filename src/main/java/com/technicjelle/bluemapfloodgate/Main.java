package com.technicjelle.bluemapfloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class Main extends JavaPlugin {
	private final boolean VERBOSE_LOGGING = false;
	private void verboseLog(String message) {
		if (VERBOSE_LOGGING) getLogger().info(message);
	}

	@Override
	public void onEnable() {
		new Metrics(this, 16426);

		BlueMapAPI.onEnable(blueMapOnEnableListener);

		getLogger().info("BlueMap Floodgate compatibility plugin enabled!");
	}

	private final Consumer<BlueMapAPI> blueMapOnEnableListener = blueMapAPI -> {
		SkinProvider floodgateSkinProvider = new SkinProvider() {
			private final FloodgateApi floodgateApi = FloodgateApi.getInstance();
			private final SkinProvider defaultSkinProvider = blueMapAPI.getPlugin().getSkinProvider();

			@Override
			public Optional<BufferedImage> load(UUID playerUUID) throws IOException {
				if (floodgateApi.isFloodgatePlayer(playerUUID)) {
					FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(playerUUID);
					String xuid = floodgatePlayer.getXuid();
					@Nullable String textureID = textureIDFromXUID(xuid);
					if (textureID == null) {
						verboseLog("TextureID for " + playerUUID + " is null");
						return Optional.empty();
					}
					@Nullable BufferedImage skin = skinFromTextureID(textureID);
					if (skin == null) {
						verboseLog("Skin for " + playerUUID + " is null");
						return Optional.empty();
					}
					verboseLog("Skin for " + playerUUID + " successfully gotten!");
					return Optional.of(skin);
				} else {
					return defaultSkinProvider.load(playerUUID);
				}
			}
		};

		blueMapAPI.getPlugin().setSkinProvider(floodgateSkinProvider);

	};

	@Override
	public void onDisable() {
		BlueMapAPI.unregisterListener(blueMapOnEnableListener);

		getLogger().info("BlueMap Floodgate compatibility plugin disabled!");
	}

	// ================================================================================================================
	// ===============================================Util Methods=====================================================
	// ================================================================================================================

	/**
	 * @param xuid XUID of the floodgate player
	 * @return the texture ID of the player, or null if it could not be found
	 */
	private @Nullable String textureIDFromXUID(@NotNull String xuid) {
		try {
			URL url = new URL("https://api.geysermc.org/v2/skin/" + xuid);
			verboseLog("Getting textureID from " + url);
			try {
				URLConnection request = url.openConnection();
				request.connect();

				JsonObject joRoot = JsonParser.parseReader(new InputStreamReader((InputStream) request.getContent())).getAsJsonObject();
				if (joRoot == null) {
					verboseLog("joRoot is null!");
					return null;
				}

				JsonElement jeTextureID = joRoot.get("texture_id");
				if (jeTextureID == null) {
					verboseLog("jeTextureID is null!");
					return null;
				}

				String textureID = jeTextureID.getAsString();
				if (textureID == null) {
					verboseLog("textureID is null!");
					return null;
				}

				return textureID;
			} catch (IOException e) {
				getLogger().warning("Failed to get the textureID for " + xuid + " from " + url);
				e.printStackTrace();
				return null;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @param textureID texture ID of the floodgate player
	 * @return the skin of the player, or null if it could not be found
	 */
	private @Nullable BufferedImage skinFromTextureID(@NotNull String textureID) {
		verboseLog("Getting skin from textureID: " + textureID);
		return imageFromURL("https://textures.minecraft.net/texture/" + textureID);
	}

	/**
	 * @param url URL of the image
	 * @return the image, or null if it could not be found
	 */
	private @Nullable BufferedImage imageFromURL(@NotNull String url) {
		BufferedImage result;
		try {
			URL imageUrl = new URL(url);
			verboseLog("Getting image from URL: " + url);
			try {
				InputStream in = imageUrl.openStream();
				result = ImageIO.read(in);
				in.close();
			} catch (IOException e) {
				getLogger().warning("Failed to get the image from " + url);
				e.printStackTrace();
				return null;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
		return result;
	}
}
