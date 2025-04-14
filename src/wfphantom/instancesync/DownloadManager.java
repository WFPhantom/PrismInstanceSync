package wfphantom.instancesync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;

import wfphantom.instancesync.Instance.Addon;

public class DownloadManager {

    private final File modsDir;
    private final String selectedSide;
    private ExecutorService executor;
    private int downloadCount;

    public DownloadManager(File modsDir, String selectedSide) {
        this.modsDir = modsDir;
        this.selectedSide = selectedSide;
    }

    public void downloadInstance(List<Addon> addons, JsonArray modList) {
        executor = Executors.newFixedThreadPool(10);

        System.out.println("Downloading any missing mods");
        long time = System.currentTimeMillis();

        for (Addon addon : addons) {
            if (shouldSkipAddon(addon)) {
                System.out.println("Skipping " + addon.filename + " (side: " + addon.side + ")");
                continue;
            }
            downloadAddonIfNeeded(addon);
        }
        if (downloadCount == 0) {
            System.out.println("No mods need to be downloaded, yay!");
        } else {
            try {
                executor.shutdown();
                boolean terminated = executor.awaitTermination(1, TimeUnit.DAYS);

                if (!terminated) {
                    System.out.println("Timeout elapsed before termination completed.");
                }
                float secs = (float) (System.currentTimeMillis() - time) / 1000F;
                System.out.printf("Finished downloading %d mods (Took %.2fs)%n%n", downloadCount, secs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        renameDisabledFiles(addons);
        deleteRemovedMods(modList);
    }
    private boolean shouldSkipAddon(Addon addon) {
        if (selectedSide.equals("both")) {
            return false;
        }
        return !addon.side.equals("both") && !addon.side.equalsIgnoreCase(selectedSide);
    }


    private void downloadAddonIfNeeded(Addon addon) {
        String filename = addon.filename;
        String fileid = addon.fileid;
        String modId = addon.modId;
        String version = addon.version;

        String actualFilename = filename.endsWith(".jar.disabled") ? filename.substring(0, filename.length() - 9) : filename;

        if (modId != null && version != null) {
            String downloadUrl = constructModrinthDownloadUrl(modId, version, actualFilename);

            File modFile = new File(modsDir, filename);
            if (!modFile.exists()) download(modFile, downloadUrl, false);
        } else if (fileid != null && !fileid.trim().isEmpty()) {
            String downloadUrl = constructCurseForgeDownloadUrl(fileid, actualFilename);
            File modFile = new File(modsDir, filename);
            if (!modFile.exists()) download(modFile, downloadUrl, true);
        } else {
            System.out.println("Skipping " + filename + " due to empty fileid and mod-id/version");
        }
    }

    private void renameDisabledFiles(List<Addon> addons) {
        for (Addon addon : addons) {
            String filename = addon.filename;
            if (filename.endsWith(".jar.disabled")) {
                String actualFilename = filename.substring(0, filename.length() - 9);
                File modFile = new File(modsDir, actualFilename);
                File disabledFile = new File(modsDir, filename);
                if (modFile.exists() && !disabledFile.exists()) {
                    if (modFile.renameTo(disabledFile)) {
                        System.out.println("Renamed " + actualFilename + " to " + filename);
                    } else {
                        System.out.println("Failed to rename " + actualFilename + " to " + filename);
                    }
                }
            }
        }
    }

    private String constructCurseForgeDownloadUrl(String fileid, String filename) {
        fileid = fileid.trim();
        if (fileid.length() < 7) {
            throw new IllegalArgumentException("Invalid fileid: " + fileid);
        }
        String firstPart = fileid.substring(0, 4);
        String secondPart = fileid.substring(4, 7);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        encodedFilename = encodedFilename.replace("+", "%20");
        return "https://media.forgecdn.net/files/" + firstPart + "/" + secondPart + "/" + encodedFilename;
    }

    private String constructModrinthDownloadUrl(String modId, String version, String filename) {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return "https://cdn.modrinth.com/data/" + modId + "/versions/" + version + "/" + encodedFilename;
    }

    private void download(final File target, final String downloadUrl, boolean useFallback) {
        Runnable run = () -> {
            String name = target.getName();
            long time = System.currentTimeMillis();

            try {
                System.out.println("Downloading " + name);
                downloadFile(target, downloadUrl);
                float secs = (float) (System.currentTimeMillis() - time) / 1000F;
                System.out.printf("Finished downloading %s (Took %.2fs)%n", name, secs);
            } catch (IOException e) {
                System.out.println("File not found at URL: " + downloadUrl);
                if (useFallback) {
                    try {
                        System.out.println("Retrying with edge.forgecdn.net");
                        downloadFile(target, downloadUrl.replace("media.forgecdn.net", "edge.forgecdn.net"));
                        float secs = (float) (System.currentTimeMillis() - time) / 1000F;
                        System.out.printf("Finished downloading %s (Took %.2fs)%n", name, secs);
                    } catch (IOException ex) {
                        System.out.println("Failed to download " + name + " from fallback URL");
                        ex.printStackTrace();
                    }
                }
            }
        };

        downloadCount++;
        executor.submit(run);
    }

    private void downloadFile(File target, String downloadUrl) throws IOException {
        URI uri = URI.create(downloadUrl);
        URL url = uri.toURL();
        URLConnection connection = url.openConnection();
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(target)) {

            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
        }
    }

    private void deleteRemovedMods(JsonArray modList) {
        System.out.println("Deleting any removed mods");

        List<String> jsonFilenames = new ArrayList<>();
        for (JsonElement element : modList) {
            JsonObject mod = element.getAsJsonObject();
            jsonFilenames.add(mod.get("filename").getAsString());
        }

        File[] files = modsDir.listFiles(f ->
                f.isFile() &&
                        (f.getName().endsWith(".jar") || f.getName().endsWith(".jar.disabled")) &&
                        !jsonFilenames.contains(f.getName())
        );

        if (files != null && files.length > 0) {
            for (File f : files) {
                System.out.println("Found removed mod " + f.getName());
                if (f.delete()) {
                    System.out.println("Deleted " + f.getName());
                } else {
                    System.out.println("Failed to delete " + f.getName());
                }
            }
            System.out.println("Deleted " + files.length + " old mods");
        } else {
            System.out.println("No mods were removed, woo!");
        }
    }
}