package com.technicjelle.bluemapfloodgate;

import org.bstats.charts.SimplePie;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import static com.technicjelle.bluemapfloodgate.Main.logger;

public class Config {

	private final Main plugin;

	private @NotNull FileConfiguration configFile() {
		return plugin.getConfig();
	}

	private final String CONFIG_VERSION_KEY = "configVersion_DO_NOT_TOUCH";
	private final int CONFIG_VERSION_NEWEST = 4; //Also change in config.yml
	private final String VERBOSE_LOGGING_KEY = "verboseLogging";
	private final boolean VERBOSE_LOGGING_DEFAULT = false;
	private final String CACHE_HOURS_KEY = "cacheHours";
	private final int CACHE_HOURS_DEFAULT = 72;
	private final String CUSTOM_API_KEY = "customAPI";
	private final String CUSTOM_API_DEFAULT = "";

	private final boolean verboseLogging;
	private final long cacheHours;
	private final String customAPI;

	public Config(@NotNull Main plugin) {
		this.plugin = plugin;

		//Setup config directory
		if(plugin.getDataFolder().mkdirs()) logger.info("Created plugin directory");

		//Setup config file
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			try {
				logger.info("Creating config file");
				Files.copy(Objects.requireNonNull(plugin.getResource("config.yml")), configFile.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		//Load config from disk
		plugin.reloadConfig();

		//Load config values into variables
		int configVersionCurrent = configFile().getInt(CONFIG_VERSION_KEY, 0);  //is 0 if the config file doesn't exist

		boolean outOfDate = configVersionCurrent != 0 && configVersionCurrent != CONFIG_VERSION_NEWEST
				//use config.contains to check for all old config settings
				|| configFile().contains("verboseUpdateMessages")
				|| configFile().contains("useTydiumCraftSkinAPI");

		if (outOfDate){
			logger.severe("Config is out of date, please delete the config.yml file and reload BlueMap to regenerate a new one.");
			logger.warning("If you have any custom settings, you will need to re-add them to the new config file.");
			logger.warning("Using built-in defaults for now.");

			cacheHours = CACHE_HOURS_DEFAULT;
			verboseLogging = VERBOSE_LOGGING_DEFAULT;
			customAPI = CUSTOM_API_DEFAULT;
		} else {
			//Config file is up-to-date
			cacheHours = configFile().getLong(CACHE_HOURS_KEY, CACHE_HOURS_DEFAULT);
			verboseLogging = configFile().getBoolean(VERBOSE_LOGGING_KEY, VERBOSE_LOGGING_DEFAULT);
			customAPI = configFile().getString(CUSTOM_API_KEY, CUSTOM_API_DEFAULT);
		}

		plugin.metrics.addCustomChart(new SimplePie("custom_api", () -> customAPI.isBlank() ? "Direct" : "Custom"));
	}

	public boolean verboseLogging() {
		return verboseLogging;
	}

	public long cacheHours() {
		return cacheHours;
	}

	public String customAPI() {
		return customAPI;
	}
}
