package jenkins.plugin.android.emulator;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.plugin.android.emulator.sdk.cli.ADBCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.AVDManagerCLIBuilder;
import jenkins.plugin.android.emulator.sdk.cli.CLICommand;
import jenkins.plugin.android.emulator.sdk.cli.EmulatorCLIBuilder;

public class EmulatorRunner {

    private String avdManager;
    private String adb;
    private String emulator;
    private EmulatorConfig config;

    public EmulatorRunner(@Nonnull EmulatorConfig config) {
        this.config = config;
    }

    public void setAVDManager(String avdManager) {
        this.avdManager = avdManager;
    }

    public void setADB(String adb) {
        this.adb = adb;
    }

    public void setEmulator(String emulator) {
        this.emulator = emulator;
    }

    // FIXME move all the code as remote callable
    public void run(@Nonnull FilePath workspace,
                    @Nonnull TaskListener listener,
                    @Nullable EnvVars env) throws IOException, InterruptedException {
        Launcher launcher = workspace.createLauncher(listener);
        if (env == null) {
            env = new EnvVars();
        }

        // start ADB service
        CLICommand cmd = ADBCLIBuilder.create(adb).start();
        executeWithTimeout(cmd, launcher, listener, env, 5l);

        String avdHome = env.get(AndroidSDKConstants.ENV_ANDROID_AVD_HOME);

        // write INI file
        File advConfig = new File(avdHome, config.getAVDName() + ".ini");
        if (!advConfig.exists()) {
            FileUtils.touch(advConfig);
        }
        Ini ini = new Ini(advConfig);
        ini.store();

        // create device
        cmd = AVDManagerCLIBuilder.create(avdManager) //
                .silent(true) //
                .createAVD(config.getAVDName());
        execute(cmd, launcher, listener, env);

        // start emulator
        cmd = EmulatorCLIBuilder.create(emulator) //
                .dataDir(avdHome) //
                .proxy(Jenkins.get().proxy) //
                .build(5554);
        execute(cmd, launcher, listener, env);
    }

    private void execute(CLICommand cmd, Launcher launcher, TaskListener listener, EnvVars env) throws IOException, InterruptedException {
        executeWithTimeout(cmd, launcher, listener, env, 0l);
    }

    private void executeWithTimeout(CLICommand cmd, Launcher launcher, TaskListener listener, EnvVars env, long timeout) throws IOException, InterruptedException {
        String executable = cmd.getExecutable();

        EnvVars procEnv = new EnvVars(env);
        procEnv.putAll(cmd.env());

        ProcStarter proc = launcher.launch().envs(procEnv) //
                .stdout(listener) //
                .pwd(new File(executable).getParent()) //
                .cmds(cmd.arguments());

        int exitCode = 0;
        if (timeout > 0) {
            exitCode = proc.start().joinWithTimeout(timeout, TimeUnit.SECONDS, listener);
        } else {
            exitCode = proc.join();
        }

        if (exitCode != 0) {
            throw new IOException(executable + " failed. exit code: " + exitCode + ".");
        }
    }

}
