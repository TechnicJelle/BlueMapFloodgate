package com.technicjelle.bluemapfloodgate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class Main extends JavaPlugin implements Listener {

	private static class CachedPlayer {
		UUID uuid;
		String xuid;
		String name;

		CachedPlayer(UUID _uuid, String _xuid, String _name) {
			uuid = _uuid;
			xuid = _xuid;
			name = _name;
		}

		//Two functions to assist the Set<CachedPlayer> in knowing if a CachedPlayer is already in the set
		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (!(obj instanceof CachedPlayer))
				return false;
			CachedPlayer in = (CachedPlayer) obj;
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

	public Metrics metrics;

	public static Logger logger;
	public static Config config;

	private FloodgateApi floodgateApi;
	private File blueMapPlayerheadsDirectory;
	private File ownPlayerheadsDirectory;

	@Nullable private HashSet<CachedPlayer> playersToProcess; //null when BlueMap is online, to keep track of players that join before BlueMap is online

	@Override
	public void onEnable() {
		metrics = new Metrics(this, 16426);

		logger = getLogger();

		floodgateApi = FloodgateApi.getInstance();
		playersToProcess = new HashSet<>();

		getServer().getPluginManager().registerEvents(this, this);

		//all actual startup and shutdown logic moved to BlueMapAPI enable/disable methods, so `/bluemap reload` also reloads this plugin
		BlueMapAPI.onEnable(blueMapOnEnableListener);
		BlueMapAPI.onDisable(blueMapOnDisableListener);
	}

	Consumer<BlueMapAPI> blueMapOnEnableListener = blueMapAPI -> {
		//Config
		config = new Config(this);


		//Directory
		ownPlayerheadsDirectory = new File(getDataFolder() + "/playerheads");
		if (ownPlayerheadsDirectory.mkdir()) logger.info(ownPlayerheadsDirectory.toString() + " directory made");

		//TODO: replace with new BlueMapAPI methods etc
		blueMapPlayerheadsDirectory = new File(blueMapAPI.getWebApp().getWebRoot() + "/assets/playerheads/");

		//Process all players that joined before BlueMap was online or while it was reloading
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

		logger.info("BlueMap Floodgate compatibility plugin enabled!");
	};

	private void playersToProcessNULL() {
		logger.severe("playersToProcess was null! Please report this on GitHub! https://github.com/TechnicJelle/BlueMapFloodgate/issues");
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (floodgateApi.isFloodgatePlayer(p.getUniqueId())) {
				FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(p.getUniqueId());
				String xuid = floodgatePlayer.getXuid();
				CachedPlayer cachedPlayer = new CachedPlayer(p.getUniqueId(), xuid, floodgatePlayer.getUsername());
				floodgateJoin(cachedPlayer);
			}
		}
	}

	Consumer<BlueMapAPI> blueMapOnDisableListener = blueMapAPI -> {
		playersToProcess = new HashSet<>();
		logger.info("BlueMap API Shutting down!");
	};

	@Override
	public void onDisable() {
		BlueMapAPI.unregisterListener(blueMapOnEnableListener);
		BlueMapAPI.unregisterListener(blueMapOnDisableListener);

		playersToProcess = null;

		logger.info("BlueMap Floodgate compatibility plugin disabled!");
	}

	private void verboseLog(String message) {
		if (config.verboseLogging()) logger.info(message);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			Player p = e.getPlayer();
			if (floodgateApi.isFloodgatePlayer(p.getUniqueId())) {
				FloodgatePlayer floodgatePlayer = floodgateApi.getPlayer(p.getUniqueId());
				String xuid = floodgatePlayer.getXuid();
				CachedPlayer cachedPlayer = new CachedPlayer(p.getUniqueId(), xuid, floodgatePlayer.getUsername());

				BlueMapAPI.getInstance().ifPresentOrElse(api -> {
					//BlueMap IS currently loaded
					floodgateJoin(cachedPlayer);
				}, () -> {
					//BlueMap is currently NOT loaded
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

	private void floodgateJoin(@NotNull CachedPlayer cachedPlayer) {
		File ownHeadFile = new File(ownPlayerheadsDirectory + "/" + cachedPlayer.uuid + ".png");
		if (ownHeadFile.exists()) {
			long lastModified = ownHeadFile.lastModified(); //long value representing the time the file was last modified, measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970)
			Calendar currentDate = Calendar.getInstance();
			long dateNow = currentDate.getTimeInMillis();
			if (dateNow > lastModified + 1000L * 60L * 60L * config.cacheHours()) {
				verboseLog("Cache for " + cachedPlayer.uuid + " outdated");
				downloadHeadToCache(cachedPlayer, ownHeadFile);

				if (ownHeadFile.setLastModified(dateNow)) {
					verboseLog(" Cache updated");
				} else {
					logger.severe(" Cache wasn't updated. This should never happen! Please report this on GitHub! https://github.com/TechnicJelle/BlueMapFloodgate/issues");
				}

			} else {
				verboseLog("Head for " + cachedPlayer.uuid + " already cached");
			}
		} else {
			downloadHeadToCache(cachedPlayer, ownHeadFile);
		}


		//TODO: replace with new BlueMapAPI methods etc
		Path destination = Paths.get(blueMapPlayerheadsDirectory.toString(), ownHeadFile.getName());
		Bukkit.getScheduler().runTaskLater(this, () -> {
			verboseLog("Overwriting BlueMap's head with floodgate's head");
			try {
				Files.copy(ownHeadFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
				verboseLog("BlueMap file overwritten!");
			} catch (IOException e) {
				logger.warning("Failed to copy the head image file from own directory to BlueMap's directory");
				e.printStackTrace();
			}
		}, 30);
	}

	private void downloadHeadToCache(CachedPlayer cachedPlayer, File f) {
		String placeholderUUID = "{UUID}";
		String placeholderName = "{NAME}";

		BufferedImage skin;

		if (config.customAPI().isBlank()) {
			String textureID = textureIDFromXUID(cachedPlayer.xuid);
			skin = skinFromTextureID(textureID);
		} else {
			String link = config.customAPI().contains(placeholderUUID)
					? config.customAPI().replace(placeholderUUID, cachedPlayer.uuid.toString())
					: config.customAPI() + cachedPlayer.uuid;
			link = link.replace(placeholderName, cachedPlayer.name);

			verboseLog("Getting " + cachedPlayer.name + "'s skin from custom Skin API: " + link);
			skin = imageFromURL(link);
		}

		BufferedImage head;

		if (skin == null) {
			logger.warning("Skin was null, falling back to Steve head!"
					+ (config.verboseLogging() ? "" : " Turn on verbose logging in the config to find out why, next time"));
			head = getSteveHead();
		} else {
			//if skin is already a head, just use that
			if (skin.getWidth() == 8 && skin.getHeight() == 8) {
				verboseLog("Skin was already a head");
				head = skin;
			} else {
				verboseLog("Skin was not a head, cropping...");
				head = headFromSkin(skin);
			}
		}

		if (head == null) {
			logger.warning("Head was null!");
		} else {
			try {
				ImageIO.write(head, "png", f);
				verboseLog(f + " saved");
			} catch (IOException e) {
				logger.warning("Failed to write the head image file to own directory");
				e.printStackTrace();
			}
		}
	}

	//TODO: Make return nullable, check usages and make sure they can handle null
	//TODO: Inverts all these nested if-statements, replace with early returns
	private String textureIDFromXUID(@NotNull String xuid) {
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
				logger.warning("Failed to get the textureID");
				e.printStackTrace();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	private BufferedImage skinFromTextureID(String textureID) {
		verboseLog("Getting skin from textureID: " + textureID);
		return imageFromURL("https://textures.minecraft.net/texture/" + textureID);
	}

	private @Nullable BufferedImage imageFromURL(String url) {
		BufferedImage result;
		try {
			URL imageUrl = new URL(url);
			try {
				InputStream in = imageUrl.openStream();
				result = ImageIO.read(in);
				in.close();
			} catch (IOException e) {
				logger.warning("Failed to get the image from " + url);
				e.printStackTrace();
				return null;
			}
		} catch (MalformedURLException e) {
			logger.warning("URL: " + url);
			e.printStackTrace();
			return null;
		}
		return result;
	}

	//TODO: replace with new BlueMapAPI methods etc
	private BufferedImage headFromSkin(@NotNull BufferedImage skin) {
		return skin.getSubimage(8, 8, 8, 8);
	}

	private @Nullable BufferedImage getSteveHead() {
		try {
			return ImageIO.read(new File(blueMapPlayerheadsDirectory.getParent() + "/steve.png"));
		} catch (IOException e) {
			logger.warning("Failed to load BlueMap's fallback steve.png!");
			e.printStackTrace();
			return null;
		}
	}
}
