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
import java.util.*;
import java.util.function.Consumer;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class Main extends JavaPlugin implements Listener {

	private static class CachedPlayer {
		UUID uuid;
		String xuid;

		CachedPlayer(UUID _uuid, String _xuid) {
			uuid = _uuid;
			xuid = _xuid;
		}

		//Two functions to assist the Set<CachedPlayer> in knowing if a CachedPlayer is already in the set
		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (!(obj instanceof CachedPlayer in))
				return false;
			boolean resultUUID = uuid.equals(in.uuid);
			boolean resultXUID = Objects.equals(xuid, in.xuid);
//			verboseLog(uuid + ":" + in.uuid + " & " + xuid + ":" + in.xuid + "==>" + resultUUID + resultXUID);
			return resultUUID && resultXUID;
		}

		@Override
		public int hashCode() {
//			verboseLog(uuid + xuid + "==>" + (uuid.toString() + xuid).hashCode());
			return (uuid.toString() + xuid).hashCode();
		}
	}

	FileConfiguration config = getConfig();
	final int CONFIG_VERSION_GLOBAL = 2;
	final String CONFIG_VERSION_KEY = "configVersion_DO_NOT_TOUCH";

	boolean verboseLogging = true;
	final String VERBOSE_LOGGING_KEY = "verboseLogging";

	boolean useTydiumCraftSkinAPI = false;
	final String USE_TYDIUMCRAFT_SKIN_API_KEY = "useTydiumCraftSkinAPI";

	long cacheHours = 3 * 24; //three days by default
	final String CACHE_HOURS_KEY = "cacheHours";

	FloodgateApi floodgateApi;
	File blueMapPlayerheadsDirectory;
	File ownPlayerheadsDirectory;

	Set<CachedPlayer> playersToProcess;

	private int loadConfig(boolean loadAll) {
		int configVersionCurrent = config.getInt(CONFIG_VERSION_KEY);  //is 0 if the config file doesn't exist
		if(loadAll) {
			verboseLogging = config.getBoolean(VERBOSE_LOGGING_KEY);
			useTydiumCraftSkinAPI = config.getBoolean(USE_TYDIUMCRAFT_SKIN_API_KEY);
			cacheHours = config.getInt(CACHE_HOURS_KEY);
		}
		return configVersionCurrent;
	}

	@Override
	public void onEnable() {
		// Plugin startup logic

		//Config

		int configVersionCurrent = loadConfig(false);

		if(configVersionCurrent != 0 && configVersionCurrent != CONFIG_VERSION_GLOBAL ||
				config.contains("verboseUpdateMessages")) //use config.contains to check for all old config settings
		{
			getLogger().severe("Config is out of date, please delete the config file and restart your server to reset it!\nShutting down the plugin...");
			Bukkit.getPluginManager().disablePlugin(this);
		} else {
			config.addDefault(CONFIG_VERSION_KEY, CONFIG_VERSION_GLOBAL);
			config.addDefault(VERBOSE_LOGGING_KEY, verboseLogging);
			config.addDefault(USE_TYDIUMCRAFT_SKIN_API_KEY, useTydiumCraftSkinAPI);
			config.addDefault(CACHE_HOURS_KEY, cacheHours);

			config.options().copyDefaults(true);
			saveConfig();

			loadConfig(true);

			//Directory
			ownPlayerheadsDirectory = new File(getDataFolder() + "/playerheads");
			if (ownPlayerheadsDirectory.mkdir()) {
				verboseLog(ownPlayerheadsDirectory.toString() + " directory made");
			}

			//floodgateAPI
			if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
				floodgateApi = FloodgateApi.getInstance();
				getLogger().info("floodgate API ready!");
			} else {
				getLogger().severe("floodgate could not be found!");
			}

			playersToProcess = new HashSet<>();

			BlueMapAPI.onEnable(blueMapOnEnableListener);

			BlueMapAPI.onDisable(blueMapOnDisableListener);

			getServer().getPluginManager().registerEvents(this, this);
			getLogger().info("BlueMap Floodgate compatibility plugin enabled!");
		}
	}

	Consumer<BlueMapAPI> blueMapOnEnableListener = blueMapAPI -> {
		blueMapPlayerheadsDirectory = new File(blueMapAPI.getWebRoot() + "/assets/playerheads/");
		getLogger().info("BlueMap API ready!");

		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
//			verboseLog(">async");
			if (playersToProcess != null) {
//				verboseLog(">not null");
				for (CachedPlayer cachedPlayer : playersToProcess) {
					floodgateJoin(cachedPlayer);
				}
				playersToProcess = null;
			} else {
				playersToProcessNULL();
			}
		});
	};

	private void playersToProcessNULL() {
		getLogger().severe("playersToProcess was null! Please report this on GitHub! https://github.com/TechnicJelle/BlueMapFloodgate/issues");
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (floodgateApi.isFloodgatePlayer(p.getUniqueId())) {
				FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(p.getUniqueId());
				String xuid = floodgatePlayer.getXuid();
				CachedPlayer cachedPlayer = new CachedPlayer(p.getUniqueId(), xuid);
				floodgateJoin(cachedPlayer);
			}
		}
	}

	Consumer<BlueMapAPI> blueMapOnDisableListener = blueMapAPI -> {
		blueMapPlayerheadsDirectory = null;
		playersToProcess = new HashSet<>();
		getLogger().info("BlueMap API Shutting down!");
	};

	@Override
	public void onDisable() {
		BlueMapAPI.unregisterListener(blueMapOnEnableListener);
		BlueMapAPI.unregisterListener(blueMapOnDisableListener);

		playersToProcess = null;

		getLogger().info("BlueMap Floodgate compatibility plugin disabled!");
	}

	private void verboseLog(String message) {
		if (verboseLogging) getLogger().info(message);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			Player p = e.getPlayer();
			if (floodgateApi.isFloodgatePlayer(p.getUniqueId())) {
				FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(p.getUniqueId());
				String xuid = floodgatePlayer.getXuid();
				CachedPlayer cachedPlayer = new CachedPlayer(p.getUniqueId(), xuid);

				BlueMapAPI.getInstance().ifPresentOrElse(api -> { //BlueMap IS currently loaded
					floodgateJoin(cachedPlayer);
				}, () -> { //BlueMap is currently NOT loaded
					if (playersToProcess != null) {
						if (playersToProcess.add(cachedPlayer)) {
							verboseLog("Added " + p.getUniqueId() + " to processing queue");
						} else {
							verboseLog("Player " + p.getUniqueId() + " was already in the processing queue");
						}
					} else {
						playersToProcessNULL();
					}
				});
			}
		});
	}

	private void floodgateJoin(CachedPlayer cachedPlayer) {
		File ownHeadFile = new File(ownPlayerheadsDirectory + "/" + cachedPlayer.uuid + ".png");
		if (ownHeadFile.exists()) {
			long lastModified = ownHeadFile.lastModified(); //long value representing the time the file was last modified, measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970)
			Calendar currentDate = Calendar.getInstance();
			long dateNow = currentDate.getTimeInMillis();
			if (dateNow > lastModified + 1000 * 60 * 60 * cacheHours) {
				verboseLog("Cache for " + cachedPlayer.uuid + " outdated");
				downloadHeadToCache(cachedPlayer, ownHeadFile);

				if (ownHeadFile.setLastModified(dateNow)) {
					verboseLog(" Cache updated");
				} else {
					getLogger().severe(" Cache wasn't updated. This should never happen! Please report this on GitHub! https://github.com/TechnicJelle/BlueMapFloodgate/issues");
				}

			} else {
				verboseLog("Head for " + cachedPlayer.uuid + " already cached");
			}
		} else {
			downloadHeadToCache(cachedPlayer, ownHeadFile);
		}

		verboseLog("Overwriting BlueMap's head with floodgate's head");

		Path destination = Paths.get(blueMapPlayerheadsDirectory.toString(), ownHeadFile.getName());
		try {
			Files.copy(ownHeadFile.toPath(), destination, REPLACE_EXISTING);
//			verboseLog("BlueMap file overwritten!");
		} catch (IOException e) {
			getLogger().warning("Failed to copy the head image file from own directory to BlueMap's directory");
			e.printStackTrace();
		}
	}

	private void downloadHeadToCache(CachedPlayer cachedPlayer, File f) {
		BufferedImage skin;

		if (useTydiumCraftSkinAPI) {
			verboseLog("Getting " + cachedPlayer.uuid + "'s skin from TydiumCraft's Skin API");
			skin = imageFromURL("https://api.tydiumcraft.net/players/skin?type=skin&download&uuid=" + cachedPlayer.uuid);
		} else {
			String textureID = textureIDFromXUID(cachedPlayer.xuid);
			skin = skinFromTextureID(textureID);
		}

		BufferedImage head;

		if (skin == null) {
			getLogger().warning("Skin was null, falling back to Steve head!"
					+ (verboseLogging ? "" : " Turn on verbose logging in the config to find out why, next time"));
			head = getSteveHead();
		} else {
			head = headFromSkin(skin);
		}

		if (head == null) {
			getLogger().warning("Head was null!");
		} else {
			try {
				ImageIO.write(head, "png", f);
				verboseLog(f + " saved");
			} catch (IOException e) {
				getLogger().warning("Failed to write the head image file to own directory");
				e.printStackTrace();
			}
		}
	}

	private String textureIDFromXUID(String xuid) {
		try {
			URL url = new URL("https://api.geysermc.org/v2/skin/" + xuid);
			try {
				URLConnection request = url.openConnection();
				request.connect();

				JsonObject joRoot = JsonParser.parseReader(new InputStreamReader((InputStream) request.getContent())).getAsJsonObject();
				if (joRoot != null) {
					JsonElement jeTextureID = joRoot.get("texture_id");
					if (jeTextureID != null) {
						String textureID = jeTextureID.getAsString();
						if (textureID != null) {
							return textureID;
						} else {
							verboseLog("textureID is null!");
						}
					} else {
						verboseLog("jeTextureID is null!");
					}
				} else {
					verboseLog("joRoot is null!");
				}
			} catch (IOException e) {
				getLogger().warning("Failed to get the textureID");
				e.printStackTrace();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private BufferedImage skinFromTextureID(String textureID) {
		return imageFromURL("https://textures.minecraft.net/texture/" + textureID);
	}

	private BufferedImage imageFromURL(String url) {
		BufferedImage result;
		try {
			URL imageUrl = new URL(url);
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
			getLogger().warning("URL: " + url);
			e.printStackTrace();
			return null;
		}
		return result;
	}

	private BufferedImage headFromSkin(BufferedImage skin) {
		return skin.getSubimage(8, 8, 8, 8);
	}

	private BufferedImage getSteveHead() {
		try {
			return ImageIO.read(new File(blueMapPlayerheadsDirectory.getParent() + "/steve.png"));
		} catch (IOException e) {
			getLogger().warning("Failed to load BlueMap's fallback steve.png!");
			e.printStackTrace();
			return null;
		}
	}
}
