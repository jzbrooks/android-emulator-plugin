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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.plugin.android.emulator.tools.AndroidSDKInstallation;
import jenkins.tasks.SimpleBuildWrapper;
import jenkins.tasks.SimpleBuildWrapper.Context;

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

    private final String emulatorTool;

    @DataBoundConstructor
    public AndroidEmulatorBuild(@CheckForNull String emulatorTool) {
        this.emulatorTool = emulatorTool;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // get specific installation for the node
        AndroidSDKInstallation sdk = AndroidSDKUtil.getAndroidSDK(emulatorTool);
        if (sdk == null) {
            throw new IOException(jenkins.plugin.android.emulator.Messages.noInstallationFound(emulatorTool));
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
    }

    /**
     * Needed for syntax snippet generator.
     *
     * @return installation name to use by this step.
     */
    public String getEmulatorTool() {
        return emulatorTool;
    }

    @Symbol("android-emulator")
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
    }
}
