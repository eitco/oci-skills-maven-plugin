package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import de.eitco.cicd.ociskills.registry.oras.OrasRegistryClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The common configuration of the goals installing skills: the sources to pull from and the directory to install
 * into.
 */
abstract class AbstractSkillPullMojo extends AbstractMojo {

    /**
     * The sources skills are pulled from - asked in their order. Example:
     * <pre>{@code
     * <sources>
     *     <source>
     *         <id>my-maven</id>
     *         <type>maven</type>
     *         <groupId>com.example.{repository.parent}</groupId>
     *         <artifactId>{repository.last}</artifactId>
     *     </source>
     *     <source>
     *         <id>my-registry</id>
     *         <type>oci</type>
     *         <registry>registry.example.com</registry>
     *         <registryServerId>my-registry</registryServerId>
     *     </source>
     * </sources>
     * }</pre>
     */
    @Parameter
    private List<SkillSourceConfiguration> sources;

    /**
     * The directory skills are installed into - one subdirectory per skill. Directories not installed by the
     * plugin are never touched.
     */
    @Parameter(property = "skill.installDirectory", defaultValue = "${project.basedir}/.claude/skills",
            required = true)
    private File installDirectory;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    private final RepositorySystem repositorySystem;
    private final SettingsDecrypter settingsDecrypter;

    protected AbstractSkillPullMojo(RepositorySystem repositorySystem, SettingsDecrypter settingsDecrypter) {
        this.repositorySystem = repositorySystem;
        this.settingsDecrypter = settingsDecrypter;
    }

    protected Path installDirectory() {
        return installDirectory.toPath();
    }

    protected RepositorySystem repositorySystem() {
        return repositorySystem;
    }

    /**
     * @return the session of the build - checking remote repositories for new versions on every access, if
     * {@link #checkForUpdates()} says so
     */
    protected RepositorySystemSession repositorySession() {
        if (!checkForUpdates()) {
            return repositorySession;
        }
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySession);
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        return session;
    }

    /**
     * @return whether maven sources must ignore the cached version metadata of the local repository
     */
    protected boolean checkForUpdates() {
        return false;
    }

    protected List<RemoteRepository> remoteRepositories() {
        return remoteRepositories == null ? List.of() : remoteRepositories;
    }

    /**
     * @return the configured sources in their order
     * @throws MojoFailureException if a source is configured incorrectly
     */
    protected List<SkillSource> sources() throws MojoFailureException {
        List<SkillSource> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (SkillSourceConfiguration source : sources == null ? List.<SkillSourceConfiguration>of() : sources) {
            String id = source.getId();
            if (id == null || id.isBlank()) {
                throw new MojoFailureException("every <source> needs an <id>");
            }
            if (!ids.add(id)) {
                throw new MojoFailureException("the <source> id " + id + " is used twice");
            }
            try {
                result.add(source(source));
            } catch (IllegalArgumentException e) {
                throw new MojoFailureException("invalid <source> " + id + ": " + e.getMessage(), e);
            }
        }
        return result;
    }

    private SkillSource source(SkillSourceConfiguration source) throws MojoFailureException {
        String type = source.getType() == null ? "" : source.getType().trim();
        return switch (type) {
            case SkillSourceConfiguration.MAVEN -> new MavenSkillSource(source.getId(),
                    new SourceTemplate("groupId", source.getGroupId()),
                    new SourceTemplate("artifactId", source.getArtifactId()),
                    repositorySystem, repositorySession(), remoteRepositories());
            case SkillSourceConfiguration.OCI -> {
                if (source.getRegistry() == null || source.getRegistry().isBlank()) {
                    throw new IllegalArgumentException("<registry> must be configured");
                }
                yield new OciSkillSource(source.getId(), source.getRegistry().trim(),
                        source.getRepository() == null || source.getRepository().isBlank() ? null
                                : new SourceTemplate("repository", source.getRepository()),
                        registryClient(source.getCaCertificates(), source.isAllowPlainHttp()),
                        credentials(source.getRegistryServerId()));
            }
            default -> throw new IllegalArgumentException("<type> must be " + SkillSourceConfiguration.MAVEN
                    + " or " + SkillSourceConfiguration.OCI + ", not '" + type + "'");
        };
    }

    protected OrasRegistryClient registryClient(List<File> caCertificates, boolean allowPlainHttp) {
        if (allowPlainHttp) {
            getLog().warn("allowPlainHttp is enabled - pulling without TLS");
        }
        return new OrasRegistryClient(new RegistryConnection(caCertificates.stream().map(File::toPath).toList(),
                null, null, null, allowPlainHttp));
    }

    protected RegistryCredentials credentials(String registryServerId) throws MojoFailureException {
        return new RegistryCredentialsResolver(settingsDecrypter).resolve(settings, registryServerId);
    }
}
