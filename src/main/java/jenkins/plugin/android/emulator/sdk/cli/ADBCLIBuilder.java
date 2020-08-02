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
import jenkins.plugin.android.emulator.AndroidSDKConstants;

/**
 * Build a command line argument for emulator command.
 * 
 * @author Nikolas Falco
 */
public class ADBCLIBuilder {

    private static final String ARG_START_SERVER = "start-server";
    private static final String ARG_KILL_SERVER = "kill-server";

    public static ADBCLIBuilder create(@Nullable String executable) {
        return new ADBCLIBuilder(Util.fixEmptyAndTrim(executable));
    }

    private String executable;
    private String serial;
    private boolean trace = false;
    private int port = 5037;
    private int maxEmulator = 16;

    private ADBCLIBuilder(@CheckForNull String executable) {
        if (executable == null) {
            throw new IllegalArgumentException("Invalid empty or null executable");
        }
        this.executable = executable;
    }

    public ADBCLIBuilder serial(String serial) {
        this.serial = serial;
        return this;
    }

    public ADBCLIBuilder port(int port) {
        if (port <= 1023) { // system ports
            throw new IllegalArgumentException("Invalid port " + port);
        }
        this.port = port;
        return this;
    }

    public ADBCLIBuilder maxEmulators(int maxEmulator) {
        this.maxEmulator = maxEmulator;
        return this;
    }

    public ADBCLIBuilder trace() {
        this.trace = true;
        return this;
    }

    public CLICommand start() {
        ArgumentListBuilder arguments = buildGlobalOptions();
        arguments.add(ARG_START_SERVER);

        return new CLICommand(arguments, buildEnvVars());
    }

    private EnvVars buildEnvVars() {
        EnvVars env = new EnvVars();
        if (trace) {
            env.put(AndroidSDKConstants.ENV_ADB_TRACE, "all,adb,sockets,packets,rwx,usb,sync,sysdeps,transport,jdwp");
        }
        env.put(AndroidSDKConstants.ENV_ADB_LOCAL_TRANSPORT_MAX_PORT, String.valueOf(5553 + (maxEmulator * 2)));
        return env;
    }

    public CLICommand stop() {
        ArgumentListBuilder arguments = buildGlobalOptions();
        arguments.add(ARG_KILL_SERVER);

        return new CLICommand(arguments, buildEnvVars());
    }

    private ArgumentListBuilder buildGlobalOptions() {
        ArgumentListBuilder arguments = new ArgumentListBuilder(executable);

        if (serial != null) {
            arguments.add("-s", serial);
        }

        arguments.add("-P", String.valueOf(port));
        return arguments;
    }

}
