package wfphantom.instancesync;

import com.google.gson.annotations.SerializedName;

public class Instance {
    public record Addon (
            String filename,
            String fileid,
            @SerializedName("mod-id")  String modId,
            String version,
            String side
    ) {}
}
