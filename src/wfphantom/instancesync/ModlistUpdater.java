package wfphantom.instancesync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModlistUpdater {
    public static void run() {
        String modlist = "modlist.json";
        File indexDir = new File("mods/.index");
        if (!indexDir.exists()) {
            System.out.println("No .index folder found, aborting");
            return;
        }

        File file = new File(modlist);
        if (!file.exists()) {
            System.out.println("Modlist not found - creating a new one");
            createEmptyModlist(file);
            return;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<String> missingFileIds = new ArrayList<>();

        try (FileReader reader = new FileReader(file)) {
            JsonArray modList = JsonParser.parseReader(reader).getAsJsonArray();
            Iterator<JsonElement> iterator = modList.iterator();
            while (iterator.hasNext()) {
                JsonObject mod = iterator.next().getAsJsonObject();
                    mod.entrySet().removeIf(entry -> !entry.getKey().equals("filename") && !entry.getKey().equals("fileid"));
                    String filename = mod.get("filename").getAsString();
                    updateModInfo(filename, mod);
                    if (!mod.has("fileid") && !mod.has("mod-id")) missingFileIds.add(filename);
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(modList, writer);
            }
            System.out.println("Modlist updated successfully.");
            if (!missingFileIds.isEmpty()) {
                System.out.println("Mods missing fileid:");
                for (String missingFileId : missingFileIds) {
                    System.out.println(missingFileId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void createEmptyModlist(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject root = new JsonObject();
        root.add("mods", newTopLevelCategoryArray());
        root.add("shaderpacks", newTopLevelCategoryArray());
        root.add("resourcepacks", newTopLevelCategoryArray());
        // root.add("datapacks", newTopLevelCategoryArray());

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("// Filename, Curseforge/Modrinth File ID, Modrinth Version ID, Side\n");
            gson.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JsonArray newTopLevelCategoryArray() {
        JsonArray outer = new JsonArray();
        outer.add(new JsonArray()); // matches: "mods": [ [] ]
        return outer;
    }

    private static void updateModInfo(String filename, JsonObject mod) {
        File indexDir = new File("mods/.index");
        File[] tomlFiles = indexDir.listFiles((dir, name) -> name.endsWith(".toml"));
        if (tomlFiles != null) {
            for (File tomlFile : tomlFiles) {
                Toml toml = new Toml().read(tomlFile);
                String originalFilename = filename;
                if (filename.endsWith(".jar.disabled")) {
                    originalFilename = filename.substring(0, filename.length() - 9);
                }
                if (originalFilename.equals(toml.getString("filename"))) {
                    Long fileId = toml.getLong("update.curseforge.file-id");
                    if (fileId != null) {
                        mod.addProperty("fileid", fileId.toString());
                    } else {
                        String modId = toml.getString("update.modrinth.mod-id");
                        String version = toml.getString("update.modrinth.version");
                        if (modId != null && version != null) {
                            mod.addProperty("mod-id", modId);
                            mod.addProperty("version", version);
                            mod.remove("fileid");
                        }
                    }
                    if (!mod.has("side")) {
                        String side = toml.getString("side");
                        if (side == null) {
                            side = "both";
                        }
                        mod.addProperty("side", side);
                    }
                    return;
                }
            }
        }
    }
}