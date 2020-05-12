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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.filters.StringInputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.android_emulator.Constants;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import jenkins.plugin.android.emulator.AndroidSDKConstants;
import jenkins.plugin.android.emulator.Messages;
import jenkins.plugin.android.emulator.sdk.cli.CLICommand;
import jenkins.plugin.android.emulator.sdk.cli.SDKManagerCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.SDKPackages;
import jenkins.plugin.android.emulator.sdk.cli.SDKPackages.SDKPackage;
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
            String osName = platform.name().toLowerCase();
            switch (platform) {
            case WINDOWS:
                osName = id.startsWith("cmdline-tools") ? "win" : osName;
                break;
            default:
                // leave default
                break;
            }
            url = url.replace("{os}", osName);

            return this;
        }
        
    }

    public enum Channel {
        STABLE(0, "Stable"), BETA(1, "Beta"), DEV(2, "Dev"), CANARY(3, "Canary");

        private final int value;
        private final String label;

        Channel(int value, String label) {
            this.value = value;
            this.label = label;
        }

        public int getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final List<String> DEFAULT_PACKAGES = Arrays.asList("platform-tools", "build-tools", "emulator", "extras;android;m2repository", "extras;google;m2repository");

    private transient Platform platform;
    private final Channel channel;

    @DataBoundConstructor
    public AndroidSDKInstaller(String id, Channel channel) {
        super(id);
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
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

        writeConfigurations(expected);
        installBasePackages(expected, log);
        return expected;
    }

    private void writeConfigurations(FilePath sdkRoot) throws IOException, InterruptedException {
        FilePath sdkHome = getSDKHome(sdkRoot);
        sdkHome.mkdirs();

        // configure DDMS
        FilePath ddmsConfig = sdkHome.child(AndroidSDKConstants.DDMS_CONFIG);
        if (!ddmsConfig.exists()) {
            String settings = "pingOptIn=false\n";
            settings += "pingId=0\n";
            ddmsConfig.write(settings, "UTF-8");
        }

        // configure for no local repositories
        FilePath localRepoCfg = sdkHome.child(AndroidSDKConstants.LOCAL_REPO_CONFIG);
        if (!localRepoCfg.exists()) {
            localRepoCfg.write("count=0", "UTF-8");
        }
    }

    private void installBasePackages(FilePath sdkRoot, TaskListener log) throws IOException, InterruptedException {
        FilePath sdkmanager = sdkRoot.child("tools").child("bin").child("sdkmanager" + platform.extension);

        String remoteSDKRoot = sdkRoot.getRemote();

        CLICommand<SDKPackages> cmdList = SDKManagerCLIBuilder.create(sdkmanager.getRemote()) //
                .obsolete(true) //
                .proxy(Jenkins.get().proxy) //
                .sdkRoot(remoteSDKRoot) //
                .channel(channel) //
                .list();

        // prepare environment variables
        EnvVars env = new EnvVars();
        env.put(Constants.ENV_VAR_ANDROID_SDK_HOME, remoteSDKRoot);
        env.putAll(cmdList.env());

        Launcher launcher = sdkmanager.createLauncher(log);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProcStarter starter = launcher.launch().envs(env) //
                .stdout(output) //
                .pwd(sdkRoot) //
                .cmds(cmdList.arguments());
        int exitCode = starter.join();
        if (exitCode != 0) {
            throw new IOException("sdkmanager failed. exit code: " + exitCode + ".");
        }

        // FIXME cache available packages for a configurable amount of hours
        List<SDKPackage> availables = cmdList.parse(output.toString("UTF-8")).getAvailable();
        List<String> components = new ArrayList<>();
        // get component with the latest version available
        DEFAULT_PACKAGES.forEach(defaultPackage -> components.add(availables.stream() //
                .filter(p -> p.getName().startsWith(defaultPackage)) // filter by component
                .filter(p -> channel != Channel.STABLE || p.getVersion().getQualifier() == null) // filter out beta versions for stable channel
                .sorted(Collections.reverseOrder()) //
                .findFirst().get().getName()));

        CLICommand<Void> cmdInstall = SDKManagerCLIBuilder.create(sdkmanager.getRemote()) //
                .obsolete(true) //
                .proxy(Jenkins.get().proxy) //
                .sdkRoot(remoteSDKRoot) //
                .channel(channel) //
                .install(components);

        launcher = sdkmanager.createLauncher(log);
        starter = launcher.launch().envs(env) //
                .stdout(log) //
                .stdin(new StringInputStream(StringUtils.repeat("y", "\r\n", DEFAULT_PACKAGES.size()))) //
                .pwd(sdkRoot) //
                .cmds(cmdInstall.arguments());
        exitCode = starter.join();
        if (exitCode != 0) {
            throw new IOException("sdkmanager failed. exit code: " + exitCode + ".");
        }
    }

    @Override
    protected FilePath findPullUpDirectory(FilePath root) throws IOException, InterruptedException {
        // do not pullup, keep original structure
        return null;
    }

    private FilePath getSDKHome(FilePath sdkRoot) {
        return sdkRoot.child(AndroidSDKConstants.ANDROID_CACHE);
    }

    @Extension
    public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<AndroidSDKInstaller> { // NOSONAR
        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == AndroidSDKInstallation.class;
        }

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

        public ListBoxModel doFillChannelItems(@QueryParameter String channel) {
            ListBoxModel channels = new ListBoxModel();
            for (Channel ch : Channel.values()) {
                channels.add(new Option(ch.getLabel(), ch.name(), ch.name().equals(channel)));
            }
            return channels;
        }
    }
}
