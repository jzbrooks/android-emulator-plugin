/*
 * The MIT License
 *
 * Copyright (c) 2020, Nikolas Falco
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.plugin.android.emulator;

import java.util.ArrayList;
import java.util.Collection;

import hudson.Util;
import hudson.plugins.android_emulator.ScreenDensity;
import hudson.plugins.android_emulator.ScreenResolution;

public class EmulatorConfig {
    public static class ValidationError {
        private final String message;

        public ValidationError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private String osVersion;
    private String screenDensity;
    private String screenResolution;

    public String getOSVersion() {
        return osVersion;
    }

    public void setOSVersion(String osVersion) {
        this.osVersion = Util.fixEmptyAndTrim(osVersion);
    }

    public String getScreenDensity() {
        return screenDensity;
    }

    public void setScreenDensity(String screenDensity) {
        this.screenDensity = Util.fixEmptyAndTrim(screenDensity);
    }

    public String getScreenResolution() {
        return screenResolution;
    }

    public void setScreenResolution(String screenResolution) {
        this.screenResolution = Util.fixEmptyAndTrim(screenResolution);
    }

    public Collection<ValidationError> validate() {
        Collection<ValidationError> errors = new ArrayList<>();
        if (osVersion == null) {
            errors.add(new ValidationError("osVersion is required"));
        }
        if (ScreenDensity.valueOf(screenDensity) == null) {
            errors.add(new ValidationError("screen density '" + screenDensity + "' not valid"));
        }
        if (ScreenResolution.valueOf(screenResolution) == null) {
            errors.add(new ValidationError("screen resolution '" + screenResolution + "' not valid"));
        }
        return errors;
    }
}