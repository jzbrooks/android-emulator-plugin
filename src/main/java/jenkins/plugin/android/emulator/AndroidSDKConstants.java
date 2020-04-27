package jenkins.plugin.android.emulator;

public final class AndroidSDKConstants {

    private AndroidSDKConstants() {
        // default constructor
    }

    public static final String ANDROID_CACHE = ".android";
    public static final String DDMS_CONFIG = "ddms.cfg";
    public static final String LOCAL_REPO_CONFIG = "repositories.cfg";

    public static final String ENV_ADB_TRACE = "ADB_TRACE";
    public static final String ENV_ADB_LOCAL_TRANSPORT_MAX_PORT = "ADB_LOCAL_TRANSPORT_MAX_PORT";
    public static final String ENV_ANDROID_AVD_HOME = "ANDROID_AVD_HOME";
}
