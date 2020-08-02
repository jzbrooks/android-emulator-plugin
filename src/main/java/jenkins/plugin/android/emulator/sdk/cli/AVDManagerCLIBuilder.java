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
package jenkins.plugin.android.emulator.sdk.cli;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import hudson.EnvVars;
import hudson.Util;
import hudson.util.ArgumentListBuilder;

/**
 * Build a command line argument for avdmanager command.
 * 
 * @author Nikolas Falco
 */
public class AVDManagerCLIBuilder {

    private static final String NO_PREFIX = "";
    private static final String ARG_SILENT = "--silent";
    private static final String ARG_VERBOSE = "--verbose";
    private static final String ARG_CLEAR_CACHE = "--clear-cache";
    private static final String ARG_CREATE = "create avd";
    private static final String ARG_DELETE = "delete avd";
    private static final String ARG_NAME = "--name";
    private static final String ARG_PACKAGE = "--package";
    private static final String ARG_FORCE = "--force";
    private static final String ARG_DEVICE = "--force";
    private static final String ARG_ABI = "--abi";
    private static final String ARG_SDCARD = "--sdcard";

    private final String executable;
    private boolean verbose;
    private boolean silent;
    private int sdcard = -1;
    private String packagePath;
    private String abi;
    private String device;

    private AVDManagerCLIBuilder(@CheckForNull String executable) {
        if (executable == null) {
            throw new IllegalArgumentException("Invalid empty or null executable");
        }
        this.executable = executable;
    }

    public static AVDManagerCLIBuilder create(@Nullable String executable) {
        return new AVDManagerCLIBuilder(Util.fixEmptyAndTrim(executable));
    }

    public AVDManagerCLIBuilder abi(String abi) {
        this.abi = abi;
        return this;
    }

    public AVDManagerCLIBuilder device(String device) {
        this.device = device;
        return this;
    }

    public AVDManagerCLIBuilder packagePath(String packagePath) {
        this.packagePath = packagePath;
        return this;
    }

    public AVDManagerCLIBuilder verbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public AVDManagerCLIBuilder obsolete(boolean silent) {
        this.silent = silent;
        return this;
    }

    /**
     * Prepare the CLI command of avdmanager to create new device.
     * 
     * @return the command line to execute.
     */
    public CLICommand createAVD(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Device name is required");
        }

        ArgumentListBuilder arguments = new ArgumentListBuilder(executable);

        addGlobalOptions(arguments);

        // action
        arguments.add(ARG_CREATE);

        // action options
        arguments.add(ARG_NAME, name);

        if (packagePath != null) {
            arguments.add(ARG_PACKAGE, packagePath);
        }
        if (device != null) {
            arguments.add(ARG_DEVICE, device);
        }
        if (abi != null) {
            arguments.add(ARG_ABI, abi);
        }
        if (sdcard != -1) {
            arguments.add(ARG_SDCARD, String.valueOf(sdcard));
        }
        arguments.add(ARG_FORCE);

        return new CLICommand(arguments, new EnvVars());
    }

    private void addGlobalOptions(ArgumentListBuilder arguments) {
        if (verbose) {
            arguments.add(ARG_VERBOSE);
        } else if (silent) {
            arguments.add(ARG_SILENT);
        }

        arguments.add(ARG_CLEAR_CACHE);
    }

    /**
     * Prepare the CLI command of sdkmanager to perform install operation.
     * 
     * @return the command line to execute.
     */
    public CLICommand deleteAVD(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Device name is required");
        }

        ArgumentListBuilder arguments = new ArgumentListBuilder(executable);

        addGlobalOptions(arguments);

        // action
        arguments.add(ARG_DELETE);

        // action options
        arguments.add(ARG_NAME, name);

        return new CLICommand(arguments, new EnvVars());
    }

}