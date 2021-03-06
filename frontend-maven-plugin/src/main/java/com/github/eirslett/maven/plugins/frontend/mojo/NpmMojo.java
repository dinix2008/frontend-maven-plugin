package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.*;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.util.Collections;

@Mojo(name="npm",  defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class NpmMojo extends AbstractFrontendMojo {

    private static final String NPM_REGISTRY_URL = "npmRegistryURL";
    
    /**
     * npm arguments. Default is "install".
     */
    @Parameter(defaultValue = "install", property = "frontend.npm.arguments", required = false)
    private String arguments;

    @Parameter(property = "frontend.npm.npmInheritsProxyConfigFromMaven", required = false, defaultValue = "true")
    private boolean npmInheritsProxyConfigFromMaven;

    /**
     * Registry override, passed as the registry option during npm install if set.
     */
    @Parameter(property = NPM_REGISTRY_URL, required = false, defaultValue = "")
    private String npmRegistryURL;

    /**
     * Server Id for access to npm registry
     */
    @Parameter(property = "npmRegistryServerId", defaultValue = "")
    private String npmRegistryServerId;

    @Parameter(property = "session", defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildContext buildContext;

    @Component(role = SettingsDecrypter.class)
    private SettingsDecrypter decrypter;

    /**
     * Skips execution of this mojo.
     */
    @Parameter(property = "skip.npm", defaultValue = "${skip.npm}")
    private boolean skip;

    @Override
    protected boolean skipExecution() {
        return this.skip;
    }

    @Override
    public synchronized void execute(FrontendPluginFactory factory) throws TaskRunnerException {
        File packageJson = new File(workingDirectory, "package.json");
        if (buildContext == null || buildContext.hasDelta(packageJson) || !buildContext.isIncremental()) {
            ProxyConfig proxyConfig = getProxyConfig();
            NpmRegistryConfig registryConfig = getRegistryConfig();
            factory.getNpmRunner(proxyConfig, registryConfig).execute(arguments, environmentVariables);
        } else {
            getLog().info("Skipping npm install as package.json unchanged");
        }
    }

    private ProxyConfig getProxyConfig() {
        if (npmInheritsProxyConfigFromMaven) {
            return MojoUtils.getProxyConfig(session, decrypter);
        } else {
            getLog().info("npm not inheriting proxy config from Maven");
            return new ProxyConfig(Collections.<ProxyConfig.Proxy>emptyList());
        }
    }

    private NpmRegistryConfig getRegistryConfig() {
        // check to see if overridden via `-D`, otherwise fallback to pom value
        final String registryURL = System.getProperty(NPM_REGISTRY_URL, npmRegistryURL);
        if (null == registryURL || registryURL.isEmpty()) {
            return null;
        }

        String username = null;
        String password = null;
        if (null != npmRegistryServerId && !npmRegistryServerId.isEmpty()) {
            Server server = MojoUtils.decryptServer(npmRegistryServerId, session, decrypter);
            if (null != server) {
                username = server.getUsername();
                password = server.getPassword();
            }
        }
        return new NpmRegistryConfig(registryURL, username, password);
    }
}
