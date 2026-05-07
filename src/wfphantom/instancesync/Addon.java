package wfphantom.instancesync;

public record Addon (
        String filename,
        String fileid,
        String modId,
        String version,
        String side
) {}