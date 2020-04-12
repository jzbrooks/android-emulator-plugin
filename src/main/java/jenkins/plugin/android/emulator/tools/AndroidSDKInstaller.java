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
package jenkins.plugin.android.emulator.tools;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Node;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import jenkins.plugin.android.emulator.Messages;

/**
 * Automatic tools installer from google.
 *
 * @author Nikolas Falco
 *
 * @since 4.0
 */
public class AndroidSDKInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public AndroidSDKInstaller(String id) {
        super(id);
    }

    public Installable getInstallable(Node node) throws IOException {
        Installable installable = getInstallable();
        if (installable == null) {
            return null;
        }

        // Cloning the installable since we're going to update its url (not cloning it wouldn't be threadsafe)
        DownloadFromUrlInstaller.Installable clone = new DownloadFromUrlInstaller.Installable();
        clone.id = installable.id;
        clone.url = installable.url;
        clone.name = installable.name;

        clone.url = null;// TODO fix URL based on node platform
        return clone;
    }

    @Extension
    public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<AndroidSDKInstaller> { // NOSONAR
        @Override
        public String getDisplayName() {
            return Messages.AndroidSDKInstaller_displayName();
        }

        @Nonnull
        @Override
        public List<? extends Installable> getInstallables() throws IOException {
            // TODO list all available SDKs
            return Collections.emptyList();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == AndroidSDKInstallation.class;
        }
    }
}
