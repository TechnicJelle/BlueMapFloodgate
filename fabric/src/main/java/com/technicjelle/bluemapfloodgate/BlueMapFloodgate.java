package com.technicjelle.bluemapfloodgate;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class BlueMapFloodgate implements ModInitializer {
	public static final String MODID = "blue-map-floodgate";
	private static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	@Override
	public void onInitialize() {
		LOGGER.info("Successfully loaded {} mod!", MODID);

		// this needs to below the LOGGER message, otherwise the game will crash
		if (FabricLoader.getInstance().isModLoaded("bluemap")) {
			BlueMapAPI.onEnable(this.setSkinProvider);
		}
	}

	private final Consumer<BlueMapAPI> setSkinProvider = blueMapAPI -> {
		SkinProvider skinProvider = getSkinProvider(blueMapAPI);

		blueMapAPI.getPlugin().setSkinProvider(skinProvider);
	};

	// TODO convert into an API so the 'paper' and 'fabric' projects can use the same code

	private SkinProvider getSkinProvider(BlueMapAPI blueMapAPI) {
		return new SkinProvider() {
			private final SkinProvider skinProvider = blueMapAPI.getPlugin().getSkinProvider();

			@Override
			public Optional<BufferedImage> load(UUID uuid) throws IOException {
				if (isFloodgatePlayer(uuid)) {
					long xuid = getXUID(uuid);

					@Nullable String textureId = SkinHttpUtils.getTextureIdFromXUID(xuid);
					if (textureId == null) {
						LOGGER.warn("Texture ID for {} is null!", uuid);
						return Optional.empty();
					}

					@Nullable BufferedImage skin = SkinHttpUtils.getSkinTexture(textureId);

					if (skin == null) {
						LOGGER.warn("Failed to get the skin for {}", uuid);
						return Optional.empty();
					}

					LOGGER.info("Successfully retrieved the skin for {}!", uuid);
					return Optional.of(skin);
				} else {
					return skinProvider.load(uuid);
				}
			}
		};
	}

	/**
	 * Checks to see if the player connecting the sever is a Floodgate player.
	 */
	private boolean isFloodgatePlayer(UUID uuid) {
		return uuid.version() == 0;
	}

	/**
     * When provided an {@code uuid}, return the least significant bits which represents
     * the {@code xuid}.
     */
	private long getXUID(UUID uuid) {
		return uuid.getLeastSignificantBits();
	}
}