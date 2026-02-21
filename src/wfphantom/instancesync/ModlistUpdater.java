package wfphantom.instancesync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moandjiezana.toml.Toml;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static wfphantom.instancesync.InstanceSync.MODLIST;

public class ModlistUpdater {
    public static void run() {
        Path modsIndexDir = Path.of("mods", ".index");
        if (!Files.isDirectory(modsIndexDir)) {
            System.out.println("No mods/.index folder found, aborting");
            return;
        }
        List<String> missingIds = new ArrayList<>();
        List<String[]> freshMods = readAddonRowsFromTomlDir(modsIndexDir, "mods", missingIds);
        Path shaderpacksDir = Path.of("shaderpacks");
        List<String[]> freshShaderpacks = Files.isDirectory(shaderpacksDir) ? readAddonRowsFromTomlDir(shaderpacksDir, "shaderpacks", missingIds) : new ArrayList<>();
        Path resourcepacksDir = Path.of("resourcepacks");
        List<String[]> freshResourcepacks = Files.isDirectory(resourcepacksDir) ? readAddonRowsFromTomlDir(resourcepacksDir, "resourcepacks", missingIds) : new ArrayList<>();
        Path datapacksDir = Path.of("datapacks");
        List<String[]> freshDatapacks = Files.isDirectory(datapacksDir) ? readAddonRowsFromTomlDir(datapacksDir, "datapacks", missingIds) : new ArrayList<>();
        Path modlistPath = Path.of(MODLIST);
        List<String[]> modsRows;
        List<String[]> shaderpackRows;
        List<String[]> resourcepackRows;
        List<String[]> datapackRows;
        if (Files.isRegularFile(modlistPath)) {
            try {
                JsonObject existing = readModlistObjectSkippingFirstLineComment(modlistPath);
                modsRows = readRowsFromCategory(existing, "mods");
                shaderpackRows = readRowsFromCategory(existing, "shaderpacks");
                resourcepackRows = readRowsFromCategory(existing, "resourcepacks");
                datapackRows = readRowsFromCategory(existing, "datapacks");
                updateCategoryInPlace(modsRows, freshMods);
                updateCategoryInPlace(shaderpackRows, freshShaderpacks);
                updateCategoryInPlace(resourcepackRows, freshResourcepacks);
                updateCategoryInPlace(datapackRows, freshDatapacks);
            } catch (Exception e) {
                System.out.println("Failed to read existing modlist, regenerating: " + e.getMessage());
                modsRows = freshMods;
                shaderpackRows = freshShaderpacks;
                resourcepackRows = freshResourcepacks;
                datapackRows = freshDatapacks;
            }
        } else {
            modsRows = freshMods;
            shaderpackRows = freshShaderpacks;
            resourcepackRows = freshResourcepacks;
            datapackRows = freshDatapacks;
        }
        syncDisabledState(Path.of("mods"), modsRows);
        syncDisabledState(shaderpacksDir, shaderpackRows);
        syncDisabledState(resourcepacksDir, resourcepackRows);
        syncDisabledState(datapacksDir, datapackRows);
        Gson gson = new Gson();
        try (BufferedWriter out = Files.newBufferedWriter(modlistPath, StandardCharsets.UTF_8)) {
            out.write("// [filename, project-id/mod-id, file-id/version, side]\n");
            out.write("{\n");
            out.write("\"mods\":[\n");
            writeRows(out, gson, modsRows);
            out.write("],\n");
            out.write("\"shaderpacks\":[\n");
            writeRows(out, gson, shaderpackRows);
            out.write("],\n");
            out.write("\"resourcepacks\":[\n");
            writeRows(out, gson, resourcepackRows);
            out.write("],\n");
            out.write("\"datapacks\":[\n");
            writeRows(out, gson, datapackRows);
            out.write("]\n");
            out.write("}\n");
            System.out.println("Wrote modlist to: " + modlistPath.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Failed to write modlist: " + e.getMessage());
        }
        if (!missingIds.isEmpty()) {
            System.out.println("\nEntries missing ids (skipped):");
            for (String s : missingIds) {
                System.out.println(s);
            }
        }
    }
    private static void updateCategoryInPlace(List<String[]> existingRows, List<String[]> freshRows) {
        Map<String, Integer> existingById1 = new HashMap<>();
        for (int i = 0; i < existingRows.size(); i++) {
            String[] r = existingRows.get(i);
            if (r != null && r.length >= 4 && r[1] != null) {
                existingById1.put(r[1], i);
            }
        }
        Set<String> seenId1 = new HashSet<>();
        for (String[] fresh : freshRows) {
            if (fresh == null || fresh.length < 4) continue;
            String id1 = fresh[1];
            if (id1 == null) continue;
            seenId1.add(id1);
            Integer idx = existingById1.get(id1);
            if (idx != null) {
                String[] existing = existingRows.get(idx);
                existing[0] = fresh[0];
                existing[1] = fresh[1];
                existing[2] = fresh[2];
            } else {
                existingRows.add(fresh);
            }
        }
        existingRows.removeIf(r -> r == null || r.length < 4 || r[1] == null || !seenId1.contains(r[1]));
    }
    private static JsonObject readModlistObjectSkippingFirstLineComment(Path modlistPath) throws IOException {
        List<String> lines = Files.readAllLines(modlistPath, StandardCharsets.UTF_8);
        if (!lines.isEmpty() && lines.getFirst().trim().startsWith("//")) {
            lines = lines.subList(1, lines.size());
        }
        String json = String.join("\n", lines).trim();
        return JsonParser.parseString(json).getAsJsonObject();
    }
    private static List<String[]> readRowsFromCategory(JsonObject root, String categoryName) {
        List<String[]> rows = new ArrayList<>();
        if (root == null || !root.has(categoryName)) return rows;

        JsonElement el = root.get(categoryName);
        if (!el.isJsonArray()) return rows;

        JsonArray arr = el.getAsJsonArray();
        for (JsonElement rowEl : arr) {
            if (!rowEl.isJsonArray()) continue;
            JsonArray r = rowEl.getAsJsonArray();
            if (r.size() < 4) continue;
            String filename = r.get(0).getAsString();
            String id1 = r.get(1).getAsString();
            String id2 = r.get(2).getAsString();
            String side = r.get(3).getAsString();

            rows.add(new String[]{filename, id1, id2, side});
        }
        return rows;
    }
    private static List<String[]> readAddonRowsFromTomlDir(Path tomlDir, String categoryName, List<String> missingIds) {
        List<String[]> rows = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tomlDir, "*.toml")) {
            for (Path tomlPath : stream) {
                try (Reader reader = Files.newBufferedReader(tomlPath, StandardCharsets.UTF_8)) {
                    Toml toml = new Toml().read(reader);
                    String filename = toml.getString("filename");
                    if (filename == null || filename.isBlank()) {
                        System.out.println("Skipping (missing filename): " + tomlPath);
                        continue;
                    }
                    String sideRaw = toml.getString("side");
                    String side = (sideRaw == null || sideRaw.isBlank()) ? "both" : sideRaw;
                    Toml update = toml.getTable("update");
                    Toml curseforge = update == null ? null : update.getTable("curseforge");
                    Toml modrinth = update == null ? null : update.getTable("modrinth");
                    if (curseforge != null) {
                        Long projectId = curseforge.getLong("project-id");
                        Long fileId = curseforge.getLong("file-id");
                        if (projectId == null || fileId == null) {
                            missingIds.add(categoryName + ": " + filename + " (missing update.curseforge.project-id/file-id)");
                            continue;
                        }
                        rows.add(new String[]{filename, projectId.toString(), fileId.toString(), side});
                        continue;
                    }
                    if (modrinth != null) {
                        String modId = modrinth.getString("mod-id");
                        String version = modrinth.getString("version");
                        if (modId == null || modId.isBlank() || version == null || version.isBlank()) {
                            missingIds.add(categoryName + ": " + filename + " (missing update.modrinth.mod-id/version)");
                            continue;
                        }
                        rows.add(new String[]{filename, modId, version, side});
                        continue;
                    }
                    missingIds.add(categoryName + ": " + filename + " (missing [update.curseforge] or [update.modrinth])");
                } catch (Exception e) {
                    System.out.println("Failed to parse: " + tomlPath + " (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to read TOMLs in " + tomlDir + ": " + e.getMessage());
        }
        return rows;
    }
    private static void syncDisabledState(Path contentDir, List<String[]> rows) {
        if (!Files.isDirectory(contentDir) || rows.isEmpty()) return;
        Set<String> present = new HashSet<>();
        Set<String> presentDisabled = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(contentDir)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                present.add(name);
                if (name.endsWith(".disabled")) presentDisabled.add(name);
            }
        } catch (IOException e) {
            System.out.println("Failed to scan " + contentDir + ": " + e.getMessage());
            return;
        }
        for (String[] row : rows) {
            String name = row[0];
            if (name == null || name.isBlank()) continue;
            if (!name.endsWith(".disabled")) {
                String disabledName = name + ".disabled";
                if (presentDisabled.contains(disabledName)) {
                    row[0] = disabledName;
                    continue;
                }
            }
            if (name.endsWith(".disabled") && !presentDisabled.contains(name)) {
                String enabledName = name.substring(0, name.length() - ".disabled".length());
                if (present.contains(enabledName)) {
                    row[0] = enabledName;
                }
            }
        }
    }
    private static void writeRows(BufferedWriter out, Gson gson, List<String[]> rows) throws IOException {
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            out.write("  [");
            out.write(gson.toJson(r[0]));
            out.write(", ");
            out.write(gson.toJson(r[1]));
            out.write(", ");
            out.write(gson.toJson(r[2]));
            out.write(", ");
            out.write(gson.toJson(r[3]));
            out.write("]");
            if (i < rows.size() - 1) out.write(",");
            out.write("\n");
        }
    }
}