package wfphantom.instancesync;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import wfphantom.instancesync.Instance.Addon;

public final class InstanceSync {

	private static final String VERSION = "1.0.3";

	public static void main(String[] args) {
		String modlist = System.getenv("MODLIST");
		if (!modlist.endsWith(".json")) {
			modlist += ".json";
		}
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
		if(mods.exists() && !mods.isDirectory()) {
			System.out.println("/mods exists but is a file, aborting");
			System.exit(1);
		}

		if(!mods.exists()) {
			System.out.println("/mods does not exist, creating");
			boolean success = mods.mkdir();
			if (!success) {
				System.out.println("Failed to create /mods directory, aborting");
				System.exit(1);
			}
			}

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

			DownloadManager manager = new DownloadManager(mods);
			manager.downloadInstance(addons, modListJson);

			float secs = (float) (System.currentTimeMillis() - time) / 1000F;
			System.out.printf("%nDone! Took %.2fs%n", secs);
		} catch (IOException e) {
			System.out.println("ERROR: Something bad happened!");
			e.printStackTrace();
		}

	}

}