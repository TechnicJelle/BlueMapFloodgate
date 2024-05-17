package com.technicjelle.bluemapfloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.technicjelle.MCUtils;
import com.technicjelle.UpdateChecker;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.util.logging.Level;

public final class BlueMapFloodgate extends JavaPlugin {
	UpdateChecker updateChecker;

	private final boolean VERBOSE_LOGGING = true;
	private void verboseLog(String message) {
		if (VERBOSE_LOGGING) getLogger().info(message);
	}

	@Override
	public void onEnable() {
		new Metrics(this, 16426);

		updateChecker = new UpdateChecker("TechnicJelle", "BlueMapFloodgate", getDescription().getVersion());
		updateChecker.checkAsync();

		BlueMapAPI.onEnable(blueMapOnEnableListener);

		getLogger().info("BlueMap Floodgate compatibility plugin enabled!");
	}

	private final Consumer<BlueMapAPI> blueMapOnEnableListener = blueMapAPI -> {
		updateChecker.logUpdateMessage(getLogger());

		SkinProvider floodgateSkinProvider = new SkinProvider() {
			private final SkinProvider defaultSkinProvider = blueMapAPI.getPlugin().getSkinProvider();

			@Override
			public Optional<BufferedImage> load(UUID playerUUID) throws IOException {
				if (isFloodgatePlayer(playerUUID)) {
					long xuid = getXuid(playerUUID);
					@Nullable String textureID = textureIDFromXUID(xuid);
					if (textureID == null) {
						getLogger().warning("TextureID for " + playerUUID + " is null");
						return Optional.empty();
					}
					@Nullable BufferedImage skin = skinFromTextureID(textureID);
					if (skin == null) {
						getLogger().warning("Skin for " + playerUUID + " is null");
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

	private boolean isFloodgatePlayer(UUID playerUUID) {
		return playerUUID.version() == 0;
	}

	private long getXuid(UUID playerUUID) {
		return playerUUID.getLeastSignificantBits();
	}

	/**
	 * @param xuid XUID of the floodgate player
	 * @return the texture ID of the player, or null if it could not be found
	 */
	private @Nullable String textureIDFromXUID(long xuid) {
		try {
			URL url = new URL("https://api.geysermc.org/v2/skin/" + xuid);
			verboseLog("Getting textureID from " + url);
			try {
				URLConnection request = url.openConnection();
				request.connect();

				JsonObject joRoot = JsonParser.parseReader(new InputStreamReader((InputStream) request.getContent())).getAsJsonObject();
				if (joRoot == null) {
					getLogger().log(Level.WARNING, "joRoot is null!");
					return null;
				}

				JsonElement jeTextureID = joRoot.get("texture_id");
				if (jeTextureID == null) {
					getLogger().log(Level.WARNING, "jeTextureID is null!");
					return null;
				}

				String textureID = jeTextureID.getAsString();
				if (textureID == null) {
					getLogger().log(Level.WARNING, "textureID is null!");
					return null;
				}

				return textureID;
			} catch (IOException e) {
				getLogger().log(Level.WARNING, "Failed to get the textureID for " + xuid + " from " + url, e);
				return null;
			}
		} catch (MalformedURLException e) {
			getLogger().log(Level.SEVERE, "Geyser API URL is malformed", e);
			return null;
		}
	}

	/**
	 * @param textureID texture ID of the floodgate player
	 * @return the skin of the player, or null if it could not be found
	 */
	private @Nullable BufferedImage skinFromTextureID(@NotNull String textureID) {
		verboseLog("Downloading skin with textureID: " + textureID);
		return MCUtils.downloadImage("https://textures.minecraft.net/texture/" + textureID);
	}
}
