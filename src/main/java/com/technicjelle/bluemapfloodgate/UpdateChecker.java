//TechnicJelle's GitHub Update Checker v1
package com.technicjelle.bluemapfloodgate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

@SuppressWarnings("SameParameterValue")
public class UpdateChecker {
	private static boolean updateAvailable = false;
	private static URL url = null;
	private static String latestVersion = null;
	private static String curVer = null;


	static void check(String author, String name, String currentVersion) {
		curVer = currentVersion;
		new Thread(() -> {
			try {
				url = new URL("https://github.com/"+author+"/"+name+"/releases/latest");
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}

			HttpURLConnection con;
			try {
				con = (HttpURLConnection) url.openConnection();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			con.setInstanceFollowRedirects(false);

			String newUrl = con.getHeaderField("Location");

			if(newUrl == null) {
				throw new RuntimeException("Did not get a redirect");
			}

			String[] split = newUrl.split("/");
			latestVersion = split[split.length - 1].replace("v", "");

			if (!latestVersion.equals(curVer)) updateAvailable = true;
		}, name + "-Update-Checker").start();
	}

	static void logUpdateMessage(Logger logger) {
		if (updateAvailable) {
			logger.warning("New version available: v" + latestVersion + " (current: v" + curVer + ")");
			logger.warning("Download it at " + url);
		}
	}
}
