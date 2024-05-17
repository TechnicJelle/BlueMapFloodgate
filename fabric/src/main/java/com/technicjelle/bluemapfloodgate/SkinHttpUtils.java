package com.technicjelle.bluemapfloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class SkinHttpUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlueMapFloodgate.MODID + "/SkinHttpUtils");

    /**
     * Get a Bedrock player's skin texture using their XUID.
     *
     * @param xuid obtained by getting the least significant bits of the UUID.
     * @return {@code String texture_id} from https://api.geysermc.org/v2/skin/{xuid}.
     */
    public static String getTextureIdFromXUID(long xuid) {
        try {
            URL url = new URL("https://api.geysermc.org/v2/skin/" + xuid);

            try {
                URLConnection request = url.openConnection();
                JsonObject json = JsonParser.parseReader(new InputStreamReader((InputStream) request.getContent())).getAsJsonObject();

                if (json == null) {
                    LOGGER.warn("No content received from {}!", url);
                    return null;
                }

                JsonElement texture = json.get("texture_id");
                String textureId = texture.getAsString();

                if (textureId == null) {
                    LOGGER.warn("Could not get the texture_id from {}!", url);
                    return null;
                }

                return textureId;
            } catch (IOException e) {
                LOGGER.warn("Failed to get the texture for {} from {}!", xuid, url);
                return null;
            }
        } catch (MalformedURLException e) {
            LOGGER.error("Geyser API URL is malformed", e);
            return null;
        }
    }

    /**
     * Retrieve a player's skin from Minecraft's texture servers.
     *
     * @param textureId obtained from {@link SkinHttpUtils#getTextureIdFromXUID}.
     * @return {@code BufferedImage} BlueMap will use to display a player's head
     * on the map website.
     */
    public static BufferedImage getSkinTexture(String textureId) {
        try {
            URL url = new URL("https://textures.minecraft.net/texture/" + textureId);

            try {
                InputStream stream = url.openStream();
                return ImageIO.read(stream);
            } catch (IOException e) {
                LOGGER.error("Failed to read input stream from {} url!", url);
            }
        } catch (MalformedURLException e) {
            LOGGER.error("Minecraft textures URL is malformed", e);
            return null;
        }

        return null;
    }
}
