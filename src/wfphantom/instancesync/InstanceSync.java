package wfphantom.instancesync;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import wfphantom.instancesync.Instance.Addon;

public final class InstanceSync {
	private static final String VERSION = "1.2.1";
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
			while (choice < 1 || choice > 6) {
				System.out.print("Enter your choice: ");
				if (scanner.hasNextInt()) {
					choice = scanner.nextInt();
				} else {
					scanner.next();
					System.out.println("Invalid input. Please enter 1-6.");
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
			JsonArray modsRows = root.getAsJsonArray("mods");
			JsonArray shaderRows = root.getAsJsonArray("shaderpacks");
			JsonArray resourceRows = root.getAsJsonArray("resourcepacks");
			JsonArray datapackRows = root.getAsJsonArray("datapacks");

			System.out.println(
					"Instance loaded, has "
							+ modsRows.size() + " mods, "
							+ shaderRows.size() + " shaderpacks, "
							+ resourceRows.size() + " resourcepacks, "
							+ datapackRows.size() + " datapacks\n"
			);

			syncMmcPackLoaderFromModlist(root);
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

	private static void syncMmcPackLoaderFromModlist(JsonObject modlistRoot) {
		JsonArray loader = modlistRoot.getAsJsonArray("loader");
		if (loader == null || loader.size() < 2) {
			System.out.println("No loader info in modlist.json, skipping mmc-pack.json loader sync");
			return;
		}

		String loaderName = loader.get(0).getAsString();
		String loaderVersion = loader.get(1).getAsString();

		if (loaderName == null || loaderName.isBlank() || loaderVersion == null || loaderVersion.isBlank()) {
			System.out.println("Loader in modlist.json is empty, skipping mmc-pack.json loader sync");
			return;
		}

		String uid = switch (loaderName) {
			case "NeoForge" -> "net.neoforged";
			case "Fabric Loader" -> "net.fabricmc.fabric-loader";
			default -> null;
		};
		if (uid == null) {
			System.out.println("Unknown loader name \"" + loaderName + "\" in modlist.json, skipping mmc-pack.json loader sync");
			return;
		}

		Path jarDir = getJarDir();
		Path parent = jarDir.getParent();
		if (parent == null) {
			System.out.println("Can't locate mmc-pack.json (jar has no parent directory), skipping loader sync");
			return;
		}

		Path mmcPack = parent.resolve("mmc-pack.json");
		if (!Files.isRegularFile(mmcPack)) {
			System.out.println("mmc-pack.json is missing, are you sure you installed Prism InstanceSync to your modpack root?");
			return;
		}
		try {
			String json = Files.readString(mmcPack, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();

			JsonArray components = root.getAsJsonArray("components");
			if (components == null) {
				components = new JsonArray();
				root.add("components", components);
			}

			JsonObject existing = null;
			for (JsonElement el : components) {
				if (!el.isJsonObject()) continue;
				JsonObject c = el.getAsJsonObject();
				JsonElement uidEl = c.get("uid");
				if (uidEl != null && uid.equals(uidEl.getAsString())) {
					existing = c;
					break;
				}
			}

			boolean changed = false;

			if (existing != null) {
				String oldVersion = existing.has("version") ? existing.get("version").getAsString() : "";
				if (!loaderVersion.equals(oldVersion)) {
					existing.addProperty("version", loaderVersion);
					existing.addProperty("cachedVersion", loaderVersion);
					changed = true;
					System.out.println("Updated loader in mmc-pack.json: " + loaderName + " " + oldVersion + " -> " + loaderVersion);
				} else {
					String oldCached = existing.has("cachedVersion") ? existing.get("cachedVersion").getAsString() : "";
					if (!loaderVersion.equals(oldCached)) {
						existing.addProperty("cachedVersion", loaderVersion);
						changed = true;
						System.out.println("Updated loader cachedVersion in mmc-pack.json: " + loaderName + " -> " + loaderVersion);
					} else {
						System.out.println("Loader in mmc-pack.json already matches modlist.json (" + loaderName + " " + loaderVersion + ")");
					}
				}
			} else {
				JsonObject newComponent = new JsonObject();
				newComponent.addProperty("uid", uid);
				newComponent.addProperty("version", loaderVersion);
				components.add(newComponent);
				changed = true;
				System.out.println("Added loader to mmc-pack.json: " + loaderName + " " + loaderVersion);
			}

			if (changed) {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				Files.writeString(mmcPack, gson.toJson(root), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			System.out.println("Failed to sync mmc-pack.json loader: " + e.getMessage());
		}
	}

	private static Path getJarDir() {
		Path path = Path.of(".");
		try {
			Path location = Path.of(InstanceSync.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			Path dir = Files.isRegularFile(location) ? location.getParent() : location;
			return dir == null ? path.toAbsolutePath() : dir.toAbsolutePath();
		} catch (URISyntaxException e) {
			return path.toAbsolutePath();
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