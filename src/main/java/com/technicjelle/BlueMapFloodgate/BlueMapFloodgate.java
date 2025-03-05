package com.technicjelle.BlueMapFloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.technicjelle.BMUtils.BMNative.BMNLogger;
import com.technicjelle.BMUtils.BMNative.BMNMetadata;
import com.technicjelle.UpdateChecker;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class BlueMapFloodgate implements Runnable {
	private BMNLogger logger;
	private UpdateChecker updateChecker;

	@Override
	public void run() {
		String addonID;
		String addonVersion;
		try {
			addonID = BMNMetadata.getAddonID(this.getClass().getClassLoader());
			addonVersion = BMNMetadata.getKey(this.getClass().getClassLoader(), "version");
			logger = new BMNLogger(this.getClass().getClassLoader());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		logger.logInfo("Starting " + addonID + " " + addonVersion);
		updateChecker = new UpdateChecker("TechnicJelle", addonID, addonVersion);
		updateChecker.checkAsync();
		BlueMapAPI.onEnable(onEnableListener);
		BlueMapAPI.onDisable(onDisableListener);
	}

	private final Consumer<BlueMapAPI> onEnableListener = api -> {
		logger.logInfo("BlueMap Floodgate compatibility plugin enabled!");
		updateChecker.getUpdateMessage().ifPresent(logger::logWarning);

		SkinProvider floodgateSkinProvider = new SkinProvider() {
			private final SkinProvider defaultSkinProvider = api.getPlugin().getSkinProvider();

			@Override
			public Optional<BufferedImage> load(UUID playerUUID) throws IOException {
				if (isFloodgatePlayer(playerUUID)) {
					long xuid = getXuid(playerUUID);
					@Nullable String textureID = textureIDFromXUID(xuid);
					if (textureID == null) {
						logger.logWarning("textureID for " + playerUUID + " is null");
						return Optional.empty();
					}
					@Nullable BufferedImage skin = skinFromTextureID(textureID);
					if (skin == null) {
						logger.logWarning("Skin for " + playerUUID + " is null");
						return Optional.empty();
					}
					logger.logDebug("Skin for " + playerUUID + " successfully retrieved!");
					return Optional.of(skin);
				} else {
					return defaultSkinProvider.load(playerUUID);
				}
			}
		};

		api.getPlugin().setSkinProvider(floodgateSkinProvider);
	};

	private final Consumer<BlueMapAPI> onDisableListener = api -> {
		logger.logInfo("BlueMap Floodgate compatibility plugin disabled!");
	};

	// ================================================================================================================
	// ===============================================Util Methods=====================================================
	// ================================================================================================================

	private boolean isFloodgatePlayer(UUID playerUUID) {
		return playerUUID.version() == 0;
	}

	private long getXuid(UUID playerUUID) {
		return playerUUID.getLeastSignificantBits();
	}

	/**
	 * @param xuid XUID of the floodgate player
	 * @return the texture ID of the player, or <code>null</code> if it could not be found
	 */
	private @Nullable String textureIDFromXUID(long xuid) {
		try {
			final URL url = new URI("https://api.geysermc.org/v2/skin/" + xuid).toURL();
			logger.logDebug("Getting textureID from " + url);
			try {
				URLConnection request = url.openConnection();
				request.connect();

				JsonObject joRoot = JsonParser.parseReader(new InputStreamReader((InputStream) request.getContent())).getAsJsonObject();
				if (joRoot == null) {
					logger.logWarning("joRoot is null!");
					return null;
				}

				JsonElement jeTextureID = joRoot.get("texture_id");
				if (jeTextureID == null) {
					logger.logWarning("jeTextureID is null!");
					return null;
				}

				String textureID = jeTextureID.getAsString();
				if (textureID == null) {
					logger.logWarning("textureID is null!");
					return null;
				}

				return textureID;
			} catch (IOException e) {
				logger.logError("Failed to get the textureID for " + xuid + " from " + url, e);
				return null;
			}
		} catch (MalformedURLException | URISyntaxException e) {
			logger.logError("Geyser API URL is malformed", e);
			return null;
		}
	}

	/**
	 * @param textureID texture ID of the floodgate player
	 * @return the skin of the player, or <code>null</code> if it could not be found
	 */
	private @Nullable BufferedImage skinFromTextureID(@NotNull String textureID) {
		try {
			final URL url = new URI("https://textures.minecraft.net/texture/" + textureID).toURL();
			logger.logDebug("Downloading skin from: " + url);
			return downloadImage(url);
		} catch (MalformedURLException | URISyntaxException e) {
			logger.logError("", e);
			return null;
		}
	}

	/**
	 * Downloads an image from the given URL.
	 *
	 * @param url URL of the image
	 * @return The image, or <code>null</code> if it could not be found
	 */
	public static @Nullable BufferedImage downloadImage(@NotNull URL url) {
		try (InputStream in = url.openStream()) {
			return ImageIO.read(in);
		} catch (IOException e) {
			return null;
		}
	}
}
