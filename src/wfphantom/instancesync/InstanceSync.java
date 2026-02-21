package wfphantom.instancesync;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import wfphantom.instancesync.Instance.Addon;

public final class InstanceSync {
	private static final String VERSION = "1.2.0";
	public static final String MODLIST = "modlist.json";

	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.equalsIgnoreCase("--dev")) {
				ModlistUpdater.run(args);
				return;
			}
		}

		System.out.println("Prism InstanceSync " + VERSION);

		long time = System.currentTimeMillis();
		File dir = new File(".");
		System.out.println("Running in " + dir.getAbsolutePath());

		File instanceFile = new File(dir, MODLIST);
		if(!instanceFile.exists()) {
			System.out.println("No modlist exists in this directory, aborting");
			return;
		}

		System.out.println("Found " + MODLIST);

		ensureDirExists(new File(dir, "mods"));
		ensureDirExists(new File(dir, "shaderpacks"));
		ensureDirExists(new File(dir, "resourcepacks"));
		ensureDirExists(new File(dir, "datapacks"));

		int choice = 0;
		for (String arg : args) {
			if (arg.startsWith("--option=")) {
				try {
					choice = Integer.parseInt(arg.substring("--option=".length()));
				} catch (NumberFormatException ignored) {
				}
			}
		}

		if (choice == 0) {
			Scanner scanner = new Scanner(System.in);

			System.out.println("Choose files to download:");
			System.out.println("1 - All");
			System.out.println("2 - Client (client + both)");
			System.out.println("3 - Server (server + both)");
			System.out.println("4 - Client-only");
			System.out.println("5 - Server-only");
			System.out.println("6 - \"both\"-side only");

			while (choice < 1 || choice > 5) {
				System.out.print("Enter your choice: ");
				if (scanner.hasNextInt()) {
					choice = scanner.nextInt();
				} else {
					scanner.next();
					System.out.println("Invalid input. Please enter 1-5.");
				}
			}
			scanner.close();
		}

		String selectedSide = switch (choice) {
			case 1 -> "all";
			case 2 -> "client";
			case 3 -> "server";
			case 4 -> "client-only";
			case 5 -> "server-only";
			case 6 -> "both-only";
			default -> throw new IllegalStateException("Unexpected value: " + choice);
		};

		System.out.println("Downloading " + selectedSide);

		try (FileReader fr = new FileReader(instanceFile)) {
			System.out.println("Reading " + MODLIST);

			JsonReader reader = new JsonReader(fr);
			reader.setStrictness(Strictness.LENIENT);

			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

			downloadCategory(root, "mods", new File(dir, "mods"), selectedSide, "mods", ".jar");
			downloadCategory(root, "shaderpacks", new File(dir, "shaderpacks"), selectedSide, "shaderpacks", ".zip");
			downloadCategory(root, "resourcepacks", new File(dir, "resourcepacks"), selectedSide, "resourcepacks", ".zip");
			downloadCategory(root, "datapacks", new File(dir, "datapacks"), selectedSide, "datapacks", ".zip");

			float secs = (float) (System.currentTimeMillis() - time) / 1000F;
			System.out.printf("%nDone! Took %.2fs%n", secs);
		} catch (IOException e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	private static void ensureDirExists(File dir) {
		if (!dir.exists() || !dir.isDirectory()) {
			System.out.println("/" + dir.getName() + " does not exist, creating");
			boolean success = dir.mkdir();
			if (!success) {
				throw new IllegalStateException("Failed to create directory: " + dir.getPath());
			}
		}
	}

	private static void downloadCategory(JsonObject root, String key, File targetDir, String selectedSide, String label, String... extensions) {
		JsonArray rows = root.getAsJsonArray(key);
		if (rows == null) {
			System.out.println("No \"" + key + "\" section in modlist, skipping");
			return;
		}

		List<Addon> addons = parseAddonsFromRows(rows);
		DownloadManager manager = new DownloadManager(targetDir, selectedSide, label, extensions);
		manager.downloadInstance(addons, rows);
	}

	private static List<Addon> parseAddonsFromRows(JsonArray rows) {
		List<Addon> addons = new ArrayList<>();
		for (JsonElement el : rows) {
			if (!el.isJsonArray()) continue;
			JsonArray r = el.getAsJsonArray();
			if (r.size() < 4) continue;

			String filename = r.get(0).getAsString();
			String id1 = r.get(1).getAsString();
			String id2 = r.get(2).getAsString();
			String side = r.get(3).getAsString();

			boolean curseforge = isDigitsOnly(id1) && isDigitsOnly(id2);
			if (curseforge) {
				addons.add(new Addon(filename, id2, null, null, side));
			} else {
				addons.add(new Addon(filename, null, id1, id2, side));
			}
		}
		return addons;
	}

	private static boolean isDigitsOnly(String s) {
		if (s == null || s.isEmpty()) return false;
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) return false;
		}
		return true;
	}
}