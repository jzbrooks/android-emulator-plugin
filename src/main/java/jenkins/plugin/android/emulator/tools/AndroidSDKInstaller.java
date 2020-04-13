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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.filters.StringInputStream;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import jenkins.plugin.android.emulator.Messages;
import net.sf.json.JSONObject;

/**
 * Automatic tools installer from google.
 *
 * @author Nikolas Falco
 *
 * @since 4.0
 */
public class AndroidSDKInstaller extends DownloadFromUrlInstaller {

    public class AndroidSDKInstallable extends NodeSpecificInstallable {

        public AndroidSDKInstallable(Installable inst) {
            super(inst);
        }

        @Override
        public NodeSpecificInstallable forNode(Node node, TaskListener log) throws IOException, InterruptedException {
            if (url == null) {
                throw new IllegalStateException("Installable " + name + " does not have a valid URL");
            }
            platform = Platform.of(node);
            url = url.replace("{os}", platform.name().toLowerCase());
            return this;
        }
        
    }

    private Platform platform;

    @DataBoundConstructor
    public AndroidSDKInstaller(String id) {
        super(id);
    }

    @Override
    public Installable getInstallable() throws IOException {
        Installable installable = super.getInstallable();
        if (installable == null) {
            return null;
        }
        return new AndroidSDKInstallable(installable);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath expected = super.performInstallation(tool, node, log);
        installBasePackages(expected, log);
        return expected;
    }

    private void installBasePackages(FilePath sdkHome, TaskListener log) throws IOException, InterruptedException {
        FilePath sdkmanager = sdkHome.child("tools").child("bin").child("sdkmanager" + platform.extension);

        Launcher launcher = sdkmanager.createLauncher(log);
        ProcStarter starter = launcher.launch().stdout(log) //
                .stdin(new StringInputStream("y\r\ny\r\ny\r\ny\r\n")) //
                .cmds(new File(sdkmanager.getRemote()), //
                        "--sdk_root=\"" + sdkHome.getRemote() + "\"", //
                        "platform-tools", "emulator", "extras;android;m2repository", "extras;google;m2repository");
        starter = starter.pwd(sdkHome);
        int exitCode = starter.join();
        if (exitCode != 0) {
            throw new IOException("sdkmanager failed. exit code: " + exitCode + ".");
        }
    }

    @Override
    protected FilePath findPullUpDirectory(FilePath root) throws IOException, InterruptedException {
        // do not pullup, keep original structure
        return null;
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
            List<Installable> installables = Collections.emptyList();

            // latest available here https://developer.android.com/studio/index.html#command-tools
            try (InputStream is = getClass().getResourceAsStream("/" + getId() + ".json")) {
                if (is != null) {
                    String data = IOUtils.toString(is);
                    JSONObject json = JSONObject.fromObject(data);
                    installables = Arrays.asList(((InstallableList) JSONObject.toBean(json, InstallableList.class)).list);
                }
            }
            return installables;
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == AndroidSDKInstallation.class;
        }
    }
}
