package wfphantom.instancesync;

import com.google.gson.annotations.SerializedName;

public class Instance {

    public static class Addon {
        public String filename;
        public String fileid;
        @SerializedName("mod-id")
        public String modId;
        public String version;
        public String side;
    }
}
