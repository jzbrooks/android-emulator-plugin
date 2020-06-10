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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.ini4j.Ini;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.plugins.android_emulator.Constants;
import jenkins.model.Jenkins;
import jenkins.plugin.android.emulator.sdk.cli.ADBCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.AVDManagerCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.EmulatorCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.SDKManagerCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.SDKPackages;
import jenkins.plugin.android.emulator.sdk.cli.Targets;
import jenkins.plugin.android.emulator.tools.AndroidSDKInstaller.Channel;
import jenkins.plugin.android.emulator.tools.ToolLocator;

public class EmulatorRunner {

    private final EmulatorConfig config;
    private final ToolLocator locator;

    public EmulatorRunner(@Nonnull EmulatorConfig config, @Nonnull ToolLocator locator) {
        this.config = config;
        this.locator = locator;
    }

    public void run(@Nonnull FilePath workspace,
                    @Nonnull TaskListener listener,
                    @Nullable EnvVars env) throws IOException, InterruptedException {
        Launcher launcher = workspace.createLauncher(listener);
        if (env == null) {
            env = new EnvVars();
        }

        ProxyConfiguration proxy = Jenkins.get().proxy;

        FilePath avdManager = locator.getAVDManager(launcher);
        if (avdManager == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.noExecutableFound(avdManager));
        }
        FilePath sdkManager = locator.getSDKManager(launcher);
        if (sdkManager == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.noExecutableFound(sdkManager));
        }
        FilePath adb = locator.getADB(launcher);
        if (adb == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.noExecutableFound(adb));
        }
        FilePath emulator = locator.getEmulator(launcher);
        if (emulator == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.noExecutableFound(emulator));
        }

        String avdHome = env.get(AndroidSDKConstants.ENV_ANDROID_AVD_HOME);
        String sdkRoot = env.get(Constants.ENV_VAR_ANDROID_SDK_ROOT); // FIXME required!

        // write INI file
        // FIXME write using callable
        File advConfig = new File(avdHome, config.getAVDName() + ".ini");
        if (!advConfig.exists()) {
            FileUtils.touch(advConfig);
        }
        Properties ini = new Properties();
        try (FileReader reader = new FileReader(advConfig)) {
            ini.load(reader);
        }
        try (FileWriter writer = new FileWriter(advConfig)) {
            ini.store(writer, null);
        }

        // check if virtual device already exists
        List<Targets> targets = AVDManagerCLIBuilder.create(avdManager) //
                .silent(true) //
                .listTargets() //
                .withEnv(env) //
                .execute();

        // remove installed components
        SDKPackages packages = SDKManagerCLIBuilder.create(sdkManager) //
            .channel(Channel.STABLE) // FIXME get that one configured in the installation tool
            .sdkRoot(sdkRoot) //
            .proxy(proxy) //
            .list() //
            .withEnv(env) //
            .execute();

        // gather required components
        Set<String> components = getComponents();
        packages.getInstalled().forEach(p -> components.remove(p.getId()));
        if (!components.isEmpty()) {
            SDKManagerCLIBuilder.create(sdkManager) //
                    .channel(Channel.STABLE) // FIXME get that one configured in the installation tool
                    .sdkRoot(sdkRoot) //
                    .proxy(proxy) //
                    .install(components) //
                    .withEnv(env) //
                    .execute(listener);
        }

        // TODO perform update only if no one is using this tool
        if (!packages.getUpdates().isEmpty()) {
            SDKManagerCLIBuilder.create(sdkManager) //
                .channel(Channel.STABLE) // FIXME get that one configured in the installation tool
                .sdkRoot(sdkRoot) //
                .proxy(proxy) //
                .update(components) //
                .withEnv(env) //
                .execute(listener);
        }

         // start ADB service
        ADBCLIBuilder.create(adb) //
                .maxEmulators(1) // FIXME set equals to the number of node executors
                .start() //
                .withEnv(env) //
                .execute(listener);

        // create device
        AVDManagerCLIBuilder.create(avdManager) //
                .silent(true) //
                .packagePath(getSystemComponent()) //
                .create(config.getAVDName()) //
                .withEnv(env) //
                .execute(listener);

        // start emulator
        EmulatorCLIBuilder.create(emulator) //
                .avdName(config.getAVDName()) //
                .dataDir(avdHome) //
                .locale(config.getLocale()) //
                .proxy(proxy) //
                .build(5554) // FIXME calculate the free using the executor number, in case of multiple emulator for this executor than store into a map <Node, port> pay attention on Node that could not be saved into an aware map.
                .withEnv(env) //
                .execute(listener);
    }

    private Set<String> getComponents() {
        Set<String> components = new LinkedHashSet<>();
        components.add(buildComponent("platforms", config.getOSVersion()));
        components.add(getSystemComponent());
        return components;
    }

    private String getSystemComponent() {
        return buildComponent("system-images", config.getOSVersion(), "default", config.getTargetABI());
    }

    private String buildComponent(String...parts) {
        return StringUtils.join(parts, ';');
    }

}
