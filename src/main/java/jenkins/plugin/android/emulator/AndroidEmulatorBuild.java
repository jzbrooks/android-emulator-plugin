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

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.android_emulator.Constants;
import hudson.plugins.android_emulator.ScreenDensity;
import hudson.plugins.android_emulator.ScreenResolution;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.plugin.android.emulator.EmulatorConfig.ValidationError;
import jenkins.plugin.android.emulator.sdk.cli.ADBCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.CLICommand;
import jenkins.plugin.android.emulator.sdk.cli.EmulatorCLIBuilder;
import jenkins.plugin.android.emulator.sdk.home.DefaultHomeLocator;
import jenkins.plugin.android.emulator.sdk.home.HomeLocator;
import jenkins.plugin.android.emulator.tools.AndroidSDKInstallation;
import jenkins.tasks.SimpleBuildWrapper;

public class AndroidEmulatorBuild extends SimpleBuildWrapper {

    private static class EnvVarsAdapter extends EnvVars {
        private static final long serialVersionUID = 1L;

        private final transient Context context; // NOSONAR

        public EnvVarsAdapter(@Nonnull Context context) {
            this.context = context;
        }

        @Override
        public String put(String key, String value) {
            context.env(key, value);
            return null; // old value does not exist, just one binding for key
        }

        @Override
        public void override(String key, String value) {
            put(key, value);
        }
    }

    private final String osVersion;
    private final String screenDensity;
    private final String screenResolution;
    private final String emulatorTool;
    private HomeLocator homeLocationStrategy;

    @DataBoundConstructor
    public AndroidEmulatorBuild(@CheckForNull String emulatorTool, String osVersion, String screenDensity, String screenResolution) {
        this.emulatorTool = Util.fixEmptyAndTrim(emulatorTool);
        this.osVersion = Util.fixEmptyAndTrim(osVersion);
        this.screenDensity = Util.fixEmptyAndTrim(screenDensity);
        this.screenResolution = Util.fixEmptyAndTrim(screenResolution);
        this.homeLocationStrategy = new DefaultHomeLocator();
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // get specific installation for the node
        AndroidSDKInstallation sdk = AndroidSDKUtil.getAndroidSDK(emulatorTool);
        if (sdk == null) {
            throw new AbortException(Messages.noInstallationFound(emulatorTool));
        }

        // replace variable in user input
        EnvVars env = initialEnvironment.overrideAll(context.getEnv());
        EmulatorConfig config = new EmulatorConfig();
        config.setOSVersion(Util.replaceMacro(osVersion, env));
        config.setScreenDensity(Util.replaceMacro(screenDensity, env));
        config.setScreenResolution(Util.replaceMacro(screenResolution, env));

        // validate input
        Collection<ValidationError> errors = config.validate();
        if (!errors.isEmpty()) {
            throw new AbortException(StringUtils.join(errors, "\n"));
        }

        Computer computer = workspace.toComputer();
        if (computer == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.nodeNotAvailable());
        }
        Node node = computer.getNode();
        if (node == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.nodeNotAvailable());
        }
        sdk = sdk.forNode(node, listener);
        sdk = sdk.forEnvironment(initialEnvironment);
        String exec = sdk.getSDKManager(launcher);
        if (exec == null) {
            throw new AbortException(jenkins.plugin.android.emulator.Messages.noExecutableFound(sdk.getHome()));
        }
        sdk.buildEnvVars(new EnvVarsAdapter(context));

        // configure home location
        FilePath homeLocation = homeLocationStrategy.locate(workspace);
        if (homeLocation != null) {
            context.env(Constants.ENV_VAR_ANDROID_SDK_HOME, homeLocation.getRemote());
            context.env(AndroidSDKConstants.ENV_ANDROID_AVD_HOME, homeLocation.child("avd").getRemote());
        }

        env = initialEnvironment.overrideAll(context.getEnv());

        // FIXME move follow to external class
        // start ADB service
        CLICommand adbStart = ADBCLIBuilder.create(sdk.getADB(launcher)).start();
        EnvVars adbEnv = new EnvVars(env);
        adbEnv.putAll(adbStart.env());
        ProcStarter adb = launcher.launch().envs(adbEnv) //
                .stdout(listener) //
                .pwd(sdk.getHome()) //
                .cmds(adbStart.arguments());
        int exitCode = adb.start().joinWithTimeout(5, TimeUnit.SECONDS, listener);
        if (exitCode != 0) {
            throw new IOException("adb failed. exit code: " + exitCode + ".");
        }

        // write INI file
        // create device
        CLICommand avdStart = EmulatorCLIBuilder.create(sdk.getEmulator(launcher)) //
                .dataDir(workspace.child(AndroidSDKConstants.ANDROID_CACHE).getRemote()) //
                .proxy(Jenkins.get().proxy) //
                .build(5554);
        EnvVars avdEnv = new EnvVars(env);
        avdEnv.putAll(avdStart.env());
        ProcStarter avd = launcher.launch().envs(avdEnv) //
                .stdout(listener) //
                .pwd(sdk.getHome()) //
                .cmds(avdStart.arguments());
        exitCode = avd.join();
        if (exitCode != 0) {
            throw new IOException("avd failed. exit code: " + exitCode + ".");
        }

        // start emulator
        CLICommand emuStart = EmulatorCLIBuilder.create(sdk.getEmulator(launcher)) //
                .dataDir(workspace.child(AndroidSDKConstants.ANDROID_CACHE).getRemote()) //
                .proxy(Jenkins.get().proxy) //
                .build(5554);
        EnvVars emuEnv = new EnvVars(env);
        emuEnv.putAll(emuStart.env());
        ProcStarter emu = launcher.launch().envs(emuEnv) //
                .stdout(listener) //
                .pwd(sdk.getHome()) //
                .cmds(emuStart.arguments());
        exitCode = emu.join();
        if (exitCode != 0) {
            throw new IOException("emulator failed. exit code: " + exitCode + ".");
        }
    }

    /**
     * Needed for syntax snippet generator.
     *
     * @return installation name to use by this step.
     */
    public String getEmulatorTool() {
        return emulatorTool;
    }

    /**
     * Needed for syntax snippet generator.
     *
     * @return the Android O.S. version.
     */
    public String getOsVersion() {
        return osVersion;
    }

    /**
     * Needed for syntax snippet generator.
     *
     * @return the screen pixel density (dpi).
     */
    public String getScreenDensity() {
        return screenDensity;
    }

    /**
     * Needed for syntax snippet generator.
     *
     * @return the screen resolution like 480x640.
     */
    public String getScreenResolution() {
        return screenResolution;
    }

    public HomeLocator getHomeLocationStrategy() {
        return homeLocationStrategy;
    }

    @DataBoundSetter
    public void setHomeLocationStrategy(HomeLocator homeLocationStrategy) {
        this.homeLocationStrategy = homeLocationStrategy == null ? new DefaultHomeLocator() : homeLocationStrategy;
    }

    @Symbol("androidEmulator")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.AndroidEmulatorBuild_displayName();
        }

        public FormValidation doCheckOsVersion(@QueryParameter @CheckForNull String osVersion) {
            if (StringUtils.isBlank(osVersion)) {
                return FormValidation.error(Messages.required());
            }
            return FormValidation.ok();
        }

        public ComboBoxModel doFillScreenDensityItems() {
            ComboBoxModel values = new ComboBoxModel();
            for (ScreenDensity density : ScreenDensity.values()) {
                values.add(density.toString());
            }
            return values;
        }

        public FormValidation doCheckScreenDensity(@QueryParameter @CheckForNull String screenDensity) {
            if (StringUtils.isBlank(screenDensity)) {
                return FormValidation.error(Messages.required());
            } else if (ScreenDensity.valueOf(screenDensity) == null) {
                return FormValidation.error(Messages.AndroidEmulatorBuild_wrongDensity());
            }
            return FormValidation.ok();
        }

        public ComboBoxModel doFillScreenResolutionItems() {
            ComboBoxModel values = new ComboBoxModel();
            for (ScreenResolution resolution : ScreenResolution.values()) {
                values.add(resolution.toString());
            }
            return values;
        }
        
        public FormValidation doCheckScreenResolution(@QueryParameter @CheckForNull String screenResolution) {
            if (StringUtils.isBlank(screenResolution)) {
                return FormValidation.error(Messages.required());
            } else if (ScreenResolution.valueOf(screenResolution) == null) {
                return FormValidation.error(Messages.AndroidEmulatorBuild_wrongDensity());
            }
            return FormValidation.ok();
        }

    }
}
