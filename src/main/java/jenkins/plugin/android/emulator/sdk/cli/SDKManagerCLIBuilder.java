package jenkins.plugin.android.emulator.sdk.cli;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import jenkins.plugin.android.emulator.tools.AndroidSDKInstaller.Channel;

public class SDKManagerCLIBuilder {

    public static class CLICommand {
        private final ArgumentListBuilder arguments;
        private final EnvVars env;

        private CLICommand(ArgumentListBuilder arguments, EnvVars env) {
            this.arguments = arguments;
            this.env = env;
        }

        public ArgumentListBuilder arguments() {
            return arguments;
        }

        public EnvVars env() {
            return env;
        }
    }

    private static final String NO_PREFIX = "";
    private static final String ARG_OBSOLETE = "--include_obsolete";
    private static final String ARG_VERBOSE = "--verbose";
    private static final String ARG_CHANNEL = "--channel";
    private static final String ARG_SDK_ROOT = "--sdk_root";
    private static final String ARG_INSTALL = "--install";
    private static final String ARG_PROXY_HOST = "--proxy_host";
    private static final String ARG_PROXY_PORT = "--proxy_port";
    private static final String ARG_PROXY_PROTOCOL = "--proxy";
    private static final String ARG_FORCE_HTTP = "--no_https";

    private final String executable;
    private ProxyConfiguration proxy;
    private String sdkRoot;
    private Channel channel;
    private boolean verbose;
    private boolean obsolete;

    private SDKManagerCLIBuilder(@CheckForNull String executable) {
        if (executable == null) {
            throw new IllegalArgumentException("Invalid empty or null executable");
        }
        this.executable = executable;
    }

    public static SDKManagerCLIBuilder create(@Nullable String executable, TaskListener log) {
        return new SDKManagerCLIBuilder(Util.fixEmptyAndTrim(executable));
    }

    public SDKManagerCLIBuilder proxy(ProxyConfiguration proxy) {
        this.proxy = proxy;
        return this;
    }

    public SDKManagerCLIBuilder sdkRoot(File sdkRoot) {
        this.sdkRoot = sdkRoot.toString();
        return this;
    }

    public SDKManagerCLIBuilder sdkRoot(String sdkRoot) {
        this.sdkRoot = sdkRoot;
        return this;
    }

    public SDKManagerCLIBuilder channel(Channel channel) {
        this.channel = channel;
        return this;
    }

    public SDKManagerCLIBuilder verbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public SDKManagerCLIBuilder obsolete(boolean obsolete) {
        this.obsolete = obsolete;
        return this;
    }

    /**
     * Prepare the CLI command of sdkmanager to perform install operation.
     * 
     * @return the command line to execute.
     */
    public CLICommand install(Collection<String> packages) {
        if (packages == null || packages.isEmpty()) {
            throw new IllegalArgumentException("At lease a packge must be specified");
        }

        ArgumentListBuilder arguments = new ArgumentListBuilder(executable);

        arguments.addKeyValuePair(NO_PREFIX, ARG_SDK_ROOT, quote(sdkRoot), false);

        if (channel != null) {
            arguments.addKeyValuePair(NO_PREFIX, ARG_CHANNEL, String.valueOf(channel.getValue()), false);
        }

        if (verbose) {
            arguments.add(ARG_VERBOSE);
        }

        if (obsolete) {
            arguments.add(ARG_OBSOLETE);
        }
        arguments.add(ARG_FORCE_HTTP);

        EnvVars env = new EnvVars();
        try {
            buildProxyEnvVars(env);
        } catch (URISyntaxException e) {
            // fallback to CLI arguments
            buildProxyArguments(arguments);
        }

        arguments.add(ARG_INSTALL);
        for (String p : packages) {
            arguments.addQuoted(p);
        }

        return new CLICommand(arguments, env);
    }

    private void buildProxyEnvVars(EnvVars env) throws URISyntaxException {
        if (proxy == null) {
            // no proxy configured
            return;
        }

        for (Pattern proxyPattern : proxy.getNoProxyHostPatterns()) {
            if (proxyPattern.matcher("https://dl.google.com/android/repository").find()) {
                // no proxy for google download repositories
                return;
            }
        }

        String userInfo = Util.fixEmptyAndTrim(proxy.getUserName());
        // append password only if userName is defined
        if (userInfo != null && StringUtils.isNotBlank(proxy.getEncryptedPassword())) {
            Secret secret = Secret.decrypt(proxy.getEncryptedPassword());
            if (secret != null) {
                userInfo = Util.fixEmptyAndTrim(secret.getPlainText());
            }
        }

        // ENV variables are used by
        // com.android.sdklib.tool.sdkmanager.SdkManagerCliSettings
        // actually authentication is not supported by the build tools !!!
        String proxyURL = new URI("http", userInfo, proxy.name, proxy.port, null, null, null).toString();
        env.put("HTTP_PROXY", proxyURL);
        env.put("HTTPS_PROXY", proxyURL);
    }

    private void buildProxyArguments(ArgumentListBuilder arguments) {
        if (proxy == null) {
            // no proxy configured
            return;
        }

        for (Pattern proxyPattern : proxy.getNoProxyHostPatterns()) {
            if (proxyPattern.matcher("https://dl.google.com/android/repository").find()) {
                // no proxy for google download repositories
                return;
            }
        }

        arguments.addKeyValuePair(NO_PREFIX, ARG_PROXY_PROTOCOL, "http", false);
        arguments.addKeyValuePair(NO_PREFIX, ARG_PROXY_HOST, proxy.name, false);
        if (proxy.port != -1) {
            arguments.addKeyValuePair(NO_PREFIX, ARG_PROXY_PORT, String.valueOf(proxy.port), false);
        }
    }

    private String quote(String quote) {
        if (!StringUtils.isNotBlank(quote)) {
            return "\"" + quote + "\"";
        }
        return quote;
    }
}
