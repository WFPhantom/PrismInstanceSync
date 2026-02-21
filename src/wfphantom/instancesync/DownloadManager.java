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

import java.util.ArrayList;

import wfphantom.instancesync.Instance.Addon;

public class DownloadManager {

    private final File targetDir;
    private final String selectedSide;
    private final String label;
    private final String[] allowedExtensions;

    private ExecutorService executor;
    private int downloadCount;

    public DownloadManager(File targetDir, String selectedSide, String label, String... allowedExtensions) {
        this.targetDir = targetDir;
        this.selectedSide = selectedSide;
        this.label = label;
        this.allowedExtensions = allowedExtensions == null ? new String[0] : allowedExtensions;
    }

    public void downloadInstance(List<Addon> addons, JsonArray rows) {
        executor = Executors.newFixedThreadPool(10);

        System.out.println("Downloading any missing " + label);
        long time = System.currentTimeMillis();

        for (Addon addon : addons) {
            if (shouldSkipAddon(addon)) {
                System.out.println("Skipping " + addon.filename() + " (side: " + addon.side() + ")");
                continue;
            }
            downloadAddonIfNeeded(addon);
        }

        if (downloadCount == 0) {
            System.out.println("No " + label + " need to be downloaded, yay!");
        } else {
            try {
                executor.shutdown();
                boolean terminated = executor.awaitTermination(1, TimeUnit.DAYS);
                if (!terminated) System.out.println("Timeout elapsed before termination completed.");
                float secs = (float) (System.currentTimeMillis() - time) / 1000F;
                System.out.printf("Finished downloading %d %s (Took %.2fs)%n%n", downloadCount, label, secs);
            } catch (InterruptedException e) {
                System.out.println("Download interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        renameDisabledFiles(addons);
        deleteRemovedFiles(rows);
    }

    private boolean shouldSkipAddon(Addon addon) {
        String side = addon.side().toLowerCase();
        return switch (selectedSide.toLowerCase()) {
            case "all" -> false;
            case "client" -> !(side.equals("client") || side.equals("both"));
            case "server" -> !(side.equals("server") || side.equals("both"));
            case "client-only" -> !side.equals("client");
            case "server-only" -> !side.equals("server");
            case "both-only" -> !side.equals("both");
            default -> true;
        };
    }

    private void downloadAddonIfNeeded(Addon addon) {
        String filename = addon.filename();
        String fileid = addon.fileid();
        String modId = addon.modId();
        String version = addon.version();

        String actualFilename = filename.endsWith(".disabled") ? filename.substring(0, filename.length() - ".disabled".length()) : filename;

        if (modId != null && version != null) {
            String downloadUrl = constructModrinthDownloadUrl(modId, version, actualFilename);

            File targetFile = new File(targetDir, filename);
            if (!targetFile.exists()) download(targetFile, downloadUrl, false);
        } else if (fileid != null && !fileid.trim().isEmpty()) {
            String downloadUrl = constructCurseForgeDownloadUrl(Long.parseLong(fileid), actualFilename);
            File targetFile = new File(targetDir, filename);
            if (!targetFile.exists()) download(targetFile, downloadUrl, true);
        } else {
            System.out.println("Skipping " + filename + " due to empty fileid and mod-id/version");
        }
    }

    private void renameDisabledFiles(List<Addon> addons) {
        for (Addon addon : addons) {
            String filename = addon.filename();
            if (filename.endsWith(".disabled")) {
                String actualFilename = filename.substring(0, filename.length() - ".disabled".length());
                File enabledFile = new File(targetDir, actualFilename);
                File disabledFile = new File(targetDir, filename);

                if (enabledFile.exists() && !disabledFile.exists()) {
                    if (enabledFile.renameTo(disabledFile)) {
                        System.out.println("Renamed " + actualFilename + " to " + filename);
                    } else {
                        System.out.println("Failed to rename " + actualFilename + " to " + filename);
                    }
                }
            }
        }
    }

    private String constructCurseForgeDownloadUrl(long fileid, String filename) {
        long firstPart = fileid / 1000;
        long secondPart = fileid % 1000;
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://mediafilez.forgecdn.net/files/" + firstPart + "/" + secondPart + "/" + encodedFilename;
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
                        downloadFile(target, downloadUrl.replace("mediafilez.forgecdn.net", "edge.forgecdn.net"));
                        float secs = (float) (System.currentTimeMillis() - time) / 1000F;
                        System.out.printf("Finished downloading %s (Took %.2fs)%n", name, secs);
                    } catch (IOException ex) {
                        System.out.println("Failed to download " + name + " from fallback URL: " + ex.getMessage());
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

    private void deleteRemovedFiles(JsonArray rows) {
        System.out.println("Deleting any removed " + label);

        File[] files = getFilesToDelete(rows);

        if (files != null && files.length > 0) {
            for (File f : files) {
                System.out.println("Found removed file " + f.getName());
                if (f.delete()) {
                    System.out.println("Deleted " + f.getName());
                } else {
                    System.out.println("Failed to delete " + f.getName());
                }
            }
            System.out.println("Deleted " + files.length + " old " + label);
        } else {
            System.out.println("No " + label + " were removed, woo!");
        }
    }

    private File[] getFilesToDelete(JsonArray rows) {
        List<String> jsonFilenames = new ArrayList<>();
        for (JsonElement element : rows) {
            if (!element.isJsonArray()) continue;
            JsonArray row = element.getAsJsonArray();
            if (row.isEmpty()) continue;
            jsonFilenames.add(row.get(0).getAsString());
        }

        return targetDir.listFiles(f ->
                f.isFile() && isManagedFileName(f.getName()) && !jsonFilenames.contains(f.getName())
        );
    }

    private boolean isManagedFileName(String name) {
        if (allowedExtensions.length == 0) return false;

        String lower = name.toLowerCase();

        for (String ext : allowedExtensions) {
            String extLower = ext.toLowerCase();
            if (lower.endsWith(extLower)) return true;
            if (lower.endsWith(extLower + ".disabled")) return true;
        }

        return false;
    }
}