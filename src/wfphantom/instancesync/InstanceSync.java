package wfphantom.instancesync;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import wfphantom.instancesync.Instance.Addon;

public final class InstanceSync {

	private static final String VERSION = "1.1.0";

	public static void main(String[] args) {
		String modlist = System.getenv("MODLIST");
		if (!modlist.endsWith(".json")) modlist += ".json";
		System.out.println("Prism InstanceSync " + VERSION);

		long time = System.currentTimeMillis();
		File dir = new File(".");
		System.out.println("Running in " + dir.getAbsolutePath());

		File instanceFile = new File(dir, modlist);
		if(!instanceFile.exists()) {
			System.out.println("No " + modlist +  " file exists in this directory, aborting");
			System.exit(1);
		}

		System.out.println("Found " + modlist);

		File mods = new File(dir, "mods");
		if(!mods.exists() || !mods.isDirectory()) {
			System.out.println("/mods does not exist, creating");
			boolean success = mods.mkdir();
			if (!success) {
				System.out.println("Failed to create /mods directory, aborting");
				System.exit(1);
			}
		}
		Scanner scanner = new Scanner(System.in);
		int choice = 0;

		System.out.println("Choose mods to download:");
		System.out.println("1 - Both sides");
		System.out.println("2 - Client-side only");
		System.out.println("3 - Server-side only");

		while (choice < 1 || choice > 3) {
			System.out.print("Enter your choice (1/2/3): ");
			if (scanner.hasNextInt()) {
				choice = scanner.nextInt();
			} else {
				scanner.next();
				System.out.println("Invalid input. Please enter 1, 2, or 3.");
			}
		}
		scanner.close();

		String selectedSide = switch (choice) {
			case 1 -> "both";
			case 2 -> "client";
			case 3 -> "server";
			default -> throw new IllegalStateException("Unexpected value: " + choice);
		};

		System.out.println("Downloading " + selectedSide);


		Gson gson = new Gson();
		try {
			System.out.println("Reading " + modlist);
			List<Addon> addons = gson.fromJson(new FileReader(instanceFile), new TypeToken<List<Addon>>(){}.getType());

			if(addons == null) {
				System.out.println("Couldn't load" + modlist + ", aborting");
				System.exit(1);
			}
			JsonArray modListJson = JsonParser.parseReader(new FileReader(instanceFile)).getAsJsonArray();

			System.out.println("Instance loaded, has " + addons.size() + " mods\n");

			DownloadManager manager = new DownloadManager(mods, selectedSide);
			manager.downloadInstance(addons, modListJson);

			float secs = (float) (System.currentTimeMillis() - time) / 1000F;
			System.out.printf("%nDone! Took %.2fs%n", secs);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}