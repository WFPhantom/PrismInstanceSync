package wfphantom.instancesync;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import wfphantom.instancesync.Instance.Addon;

public final class InstanceSync {
	private static final String VERSION = "1.2.0";
	public static final String MODLIST = "modlist.json";

	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.equalsIgnoreCase("--dev")) {
				ModlistUpdater.run();
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

		File mods = new File(dir, "mods");
		if(!mods.exists() || !mods.isDirectory()) {
			System.out.println("/mods does not exist, creating");
			boolean success = mods.mkdir();
			if (!success) {
				System.out.println("Failed to create /mods directory, aborting");
				return;
			}
		}

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

			System.out.println("Choose mods to download:");
			System.out.println("1 - All (Use for playing on both singleplayer and multiplayer worlds)");
			System.out.println("2 - Client (Use if only connecting to servers hosting the modpack and not playing singleplayer)");
			System.out.println("3 - Server (Use if hosting the modpack)");
			System.out.println("4 - Client-side only");
			System.out.println("5 - Server-side only");

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
			default -> throw new IllegalStateException("Unexpected value: " + choice);
		};

		System.out.println("Downloading " + selectedSide);


		Gson gson = new Gson();
		try (FileReader reader = new FileReader(instanceFile)) {
			System.out.println("Reading " + MODLIST);

			JsonElement root = JsonParser.parseReader(reader);
			JsonArray modListJson = root.getAsJsonArray();

			List<Addon> addons = gson.fromJson(modListJson, new TypeToken<List<Addon>>() {}.getType());
			if (addons == null) {
				System.out.println("Couldn't load modlist, aborting");
				return;
			}

			System.out.println("Instance loaded, has " + addons.size() + " mods\n");

			DownloadManager manager = new DownloadManager(mods, selectedSide);
			manager.downloadInstance(addons, modListJson);

			float secs = (float) (System.currentTimeMillis() - time) / 1000F;
			System.out.printf("%nDone! Took %.2fs%n", secs);
		} catch (IOException e) {
			System.out.println("Error: " + e.getMessage());
		}
	}
}