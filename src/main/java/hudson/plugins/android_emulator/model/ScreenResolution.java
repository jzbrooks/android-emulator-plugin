package hudson.plugins.android_emulator.model;

import hudson.Util;
import hudson.plugins.android_emulator.Constants;

import java.io.Serializable;

public class ScreenResolution implements Serializable {

    private static final long serialVersionUID = 1L;

    static final ScreenResolution QVGA = new ScreenResolution(240, 320, "QVGA", "QVGA");
    static final ScreenResolution WQVGA = new ScreenResolution(240, 400, "WQVGA", "WQVGA400");
    static final ScreenResolution FWQVGA = new ScreenResolution(240, 432, "FWQVGA", "WQVGA432");
    static final ScreenResolution HVGA = new ScreenResolution(320, 480, "HVGA", "HVGA");
    static final ScreenResolution WVGA = new ScreenResolution(480, 800, "WVGA", "WVGA800");
    static final ScreenResolution FWVGA = new ScreenResolution(480, 854, "FWVGA", "WVGA854");
    static final ScreenResolution WSVGA = new ScreenResolution(1024, 654, "WSVGA", "WSVGA");
    static final ScreenResolution WXGA_720 = new ScreenResolution(1280, 720, "WXGA720", "WXGA720");
    static final ScreenResolution WXGA_800 = new ScreenResolution(1280, 800, "WXGA800", "WXGA800");
    static final ScreenResolution WXGA = new ScreenResolution(1280, 800, "WXGA", "WXGA");
    public static final ScreenResolution[] PRESETS = new ScreenResolution[] { QVGA, WQVGA, FWQVGA, HVGA,
                                                                       WVGA, FWVGA, WSVGA,
                                                                       WXGA_720, WXGA_800, WXGA };

    private final int width;
    private final int height;
    private final String alias;
    private final String skinName;

    private ScreenResolution(int width, int height, String alias, String skinName) {
        this.width = width;
        this.height = height;
        this.alias = alias;
        this.skinName = skinName;
    }

    private ScreenResolution(int width, int height) {
        this(width, height, null, null);
    }

    public static ScreenResolution valueOf(String resolution) {
        if (Util.fixEmptyAndTrim(resolution) == null) {
            return null;
        }

        // Try matching against aliases
        for (ScreenResolution preset : PRESETS) {
            if (resolution.equalsIgnoreCase(preset.alias)) {
                return preset;
            }
        }

        // Check for pixel values
        resolution = resolution.toLowerCase();
        if (!resolution.matches(Constants.REGEX_SCREEN_RESOLUTION)) {
            return null;
        }

        // Try matching against pixel values
        int index = resolution.indexOf('x');
        int width = 0;
        int height = 0;
        try {
            width = Integer.parseInt(resolution.substring(0, index));
            height = Integer.parseInt(resolution.substring(index+1));
        } catch (NumberFormatException ex) {
            return null;
        }
        for (ScreenResolution preset : PRESETS) {
            if (width == preset.width && height == preset.height) {
                return preset;
            }
        }

        // Return custom value
        return new ScreenResolution(width, height);
    }

    public boolean isCustomResolution() {
        return alias == null;
    }

    public String getSkinName() {
        if (isCustomResolution()) {
            return getDimensionString();
        }

        return skinName;
    }

    public String getDimensionString() {
        return width +"x"+ height;
    }

    @Override
    public String toString() {
        if (isCustomResolution()) {
            return getDimensionString();
        }

        return alias;
    };

}
