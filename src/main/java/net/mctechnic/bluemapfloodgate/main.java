package net.mctechnic.bluemapfloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class main extends JavaPlugin implements Listener {


	FileConfiguration config = getConfig();
	boolean verboseUpdateMessages = true;
	long cacheHours = 3 * 24; //three days by default

	FloodgateApi floodgateApi;
	String blueMapPlayerheadsDirectory;
	File ownPlayerheadsDirectory;

	@Override
	public void onEnable() {
		// Plugin startup logic
		config.addDefault("verboseUpdateMessages", true);
		config.addDefault("cacheHours", 72);
		config.options().copyDefaults(true);
		saveConfig();

		verboseUpdateMessages = config.getBoolean("verboseUpdateMessages");
		cacheHours = config.getInt("cacheHours");

		if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
			floodgateApi = FloodgateApi.getInstance();
			getLogger().info("floodgate API ready!");
		}

		BlueMapAPI.onEnable(blueMapAPI -> {
			getLogger().info("BlueMap API ready!");
			blueMapPlayerheadsDirectory = "bluemap/web/assets/playerheads/"; //TODO: webroot
		});

		ownPlayerheadsDirectory = new File(getDataFolder() + "/playerheads");
		if(ownPlayerheadsDirectory.mkdir()){
			verboseLog(ownPlayerheadsDirectory.toString() + " directory made");
		}

		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}

	private void verboseLog(String message) {
		if (verboseUpdateMessages) getLogger().info(message);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			Player p = e.getPlayer();
			if (floodgateApi.isFloodgatePlayer(p.getUniqueId())) {
				floodgateJoin(p);
			}
		});
	}

	private void getHeadToOwnFolder(UUID uuid, File f) {
		FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(uuid);
		String xuid = floodgatePlayer.getXuid();
		String textureID = getTextureID(xuid);
		BufferedImage skin = getSkinFromID(textureID);
		if (skin != null) {
			BufferedImage head = skin.getSubimage(8, 8, 8, 8);
			try {
				ImageIO.write(head, "png", f);
				verboseLog(f +  " saved");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void floodgateJoin(Player p) {
		File ownHeadFile = new File(ownPlayerheadsDirectory + "/" + p.getUniqueId() + ".png");
		if (ownHeadFile.exists()) {
			long lastModified = ownHeadFile.lastModified(); //long value representing the time the file was last modified, measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970)
			Calendar currentDate = Calendar.getInstance();
			long dateNow = currentDate.getTimeInMillis();
			if (dateNow > lastModified + 1000 * 60 * 60 * cacheHours) {
				verboseLog("Cache for " + p.getUniqueId() + " outdated");
				getHeadToOwnFolder(p.getUniqueId(), ownHeadFile);

				if (ownHeadFile.setLastModified(dateNow)) {
					verboseLog(" Cache updated");
				} else {
					getLogger().warning(" Cache wasn't updated. This should never happen");
				}

			} else {
				verboseLog("Head for " + p.getUniqueId() + " already cached");
			}
		} else {
			getHeadToOwnFolder(p.getUniqueId(), ownHeadFile);
		}

		verboseLog("Overwriting BlueMap's head with floodgate head");

		Path destination = Paths.get(blueMapPlayerheadsDirectory, ownHeadFile.getName());
		try {
			Files.copy(ownHeadFile.toPath(), destination, REPLACE_EXISTING);
//			verboseLog("BlueMap file overwritten!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getTextureID(String xuid) {
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

	private BufferedImage getSkinFromID(String textureID) {
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
}
