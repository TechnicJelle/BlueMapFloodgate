package net.mctechnic.bluemapfloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public final class main extends JavaPlugin implements Listener {

	FloodgateApi floodgateApi;

	@Override
	public void onEnable() {
		// Plugin startup logic

		if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
			floodgateApi = FloodgateApi.getInstance();
			getLogger().info("floodgate API ready!");
		}

		BlueMapAPI.onEnable(blueMapAPI -> {
			getLogger().info("BlueMap API ready!");
			getLogger().info("yep yep");
		});

		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			Player p = e.getPlayer();
			if (floodgateApi.isFloodgatePlayer(p.getUniqueId())) {
				FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(p.getUniqueId());
				String xuid = floodgatePlayer.getXuid();
				String textureID = getTextureID(xuid);
				BufferedImage skin = getSkinFromID(textureID);
				if (skin != null) {
					BufferedImage head = skin.getSubimage(8, 8, 8, 8);
					try {
						ImageIO.write(head, "png", new File("bluemap/web/assets/playerheads/" + p.getUniqueId() + ".png")); //TODO: webroot
					} catch (IOException ioException) {
						ioException.printStackTrace();
					}
				}
			}
		});
	}

	String getTextureID(String xuid) {
		URL url;
		try {
			url = new URL("https://api.geysermc.org/v1/skin/" + xuid);
			try {
				URLConnection request = url.openConnection();
				request.connect();

				// Convert to a JSON object to print data
				JsonParser jp = new JsonParser(); //from gson
				JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent())); //Convert the input stream to a json element
				JsonObject rootObj = root.getAsJsonObject(); //This may be an array, or it may be an object.
				JsonObject data = rootObj.getAsJsonObject("data");
				return data.get("texture_id").getAsString();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	BufferedImage getSkinFromID(String textureID) {
		BufferedImage result;
		try {
			URL imageUrl = new URL("http://textures.minecraft.net/texture/" + textureID);
			try {
				InputStream in = imageUrl.openStream();
				result = ImageIO.read(in);
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
		return result;
	}

	BufferedImage getBImgFromURL(String uuid) {
		BufferedImage result;
		try {
			URL imageUrl = new URL("https://crafatar.com/avatars/" + uuid + ".png?size=8&overlay=true"); //TODO: get from config later on
			try {
				InputStream in = imageUrl.openStream();
				result = ImageIO.read(in);
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
		return result;
	}

	String name2UUID(String name) {
		URL url;
		try {
			url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
			try {
				URLConnection request = url.openConnection();
				request.connect();

				// Convert to a JSON object to print data
				JsonParser jp = new JsonParser(); //from gson
				JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent())); //Convert the input stream to a json element
				JsonObject rootObj = root.getAsJsonObject(); //This may be an array, or it may be an object.
				return rootObj.get("id").getAsString();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}
}
