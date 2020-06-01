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
import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import jenkins.plugin.android.emulator.sdk.cli.AVDManagerCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.Targets;
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

        String avdHome = env.get(AndroidSDKConstants.ENV_ANDROID_AVD_HOME);

        // write INI file
        File advConfig = new File(avdHome, config.getAVDName() + ".ini");
        if (!advConfig.exists()) {
            FileUtils.touch(advConfig);
        }
        Ini ini = new Ini(advConfig);
        ini.store();

        // check if virtual device already exists
        List<Targets> targets = AVDManagerCLIBuilder.create(locator.getAVDManager(launcher)) //
                .silent(true) //
                .listTargets() //
                .withEnv(env) //
                .execute();

        // gather required components
        
        // remove installed components
        
//        // start ADB service
//        CLICommand cmd = ADBCLIBuilder.create(adb).start();
//        executeWithTimeout(cmd, launcher, listener, env, 5l);
//
        // create device
//        cmd = AVDManagerCLIBuilder.create(avdManager) //
//                .silent(true) //
//                .create(config.getAVDName());
//        execute(cmd, launcher, listener, env);
//
//        // start emulator
//        cmd = EmulatorCLIBuilder.create(emulator) //
//                .dataDir(avdHome) //
//                .proxy(Jenkins.get().proxy) //
//                .build(5554);
//        execute(cmd, launcher, listener, env);
    }

}
