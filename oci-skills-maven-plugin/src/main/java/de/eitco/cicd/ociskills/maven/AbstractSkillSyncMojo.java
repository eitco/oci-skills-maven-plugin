package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillInstaller;
import de.eitco.cicd.ociskills.core.install.SkillLockFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.eclipse.aether.RepositorySystem;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * Installs the requested skills in the versions of the lock file - resolving requests that are not locked yet.
 */
abstract class AbstractSkillSyncMojo extends AbstractSkillPullMojo {

    /**
     * The skills to install. Example:
     * <pre>{@code
     * <skills>
     *     <skill>
     *         <id>acme/nexus-test</id>
     *         <version>[1.0,2.0)</version>
     *         <repository>teams/acme/skills</repository>
     *     </skill>
     *     <skill>
     *         <id>acme/release-notes</id>
     *         <version>2.1.0</version>
     *         <status>testing</status>
     *         <source>my-registry</source>
     *     </skill>
     *     <skill>
     *         <id>acme/lint</id>
     *         <version>[1.0,2.0)</version>
     *         <image>registry.example.com/teams/acme/skills/lint</image>
     *     </skill>
     *     <skill>
     *         <id>acme/review</id>
     *         <version>1.0.0</version>
     *         <groupId>com.example.acme</groupId>
     *         <artifactId>acme-skills</artifactId>
     *     </skill>
     * </skills>
     * }</pre>
     * A skill with {@code <image>} or {@code <groupId>} and {@code <artifactId>} is pulled from there only - the
     * sources are not asked.
     */
    @Parameter
    private List<SkillConfiguration> skills;

    /**
     * The lock file pinning version and digest of every requested skill. Commit it, so all builds install the same
     * skills and updates are visible in reviews.
     */
    @Parameter(property = "skill.lockFile", defaultValue = "${project.basedir}/" + SkillLockFile.DEFAULT_FILE_NAME,
            required = true)
    private File lockFile;

    /**
     * A directory for downloaded images - deleted after the goal.
     */
    @Parameter(defaultValue = "${project.build.directory}/skill-pull", required = true)
    private File workDirectory;

    /**
     * Skips installing skills.
     */
    @Parameter(property = "skill.sync.skip", defaultValue = "false")
    private boolean skip;

    protected AbstractSkillSyncMojo(RepositorySystem repositorySystem, SettingsDecrypter settingsDecrypter) {
        super(repositorySystem, settingsDecrypter);
    }

    /**
     * @return the locked skills to resolve again
     */
    protected abstract Predicate<SkillId> update(List<SkillRequest> requests) throws MojoFailureException;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("skill.sync.skip is set - skipping");
            return;
        }
        List<SkillRequest> requests = requests();
        SkillSynchronizer synchronizer = new SkillSynchronizer(new SkillResolver(sources()),
                new SkillInstaller(installDirectory()), new SkillLockFile(lockFile.toPath()), workDirectory.toPath(),
                getLog()::info);
        try {
            synchronizer.sync(requests, update(requests));
        } catch (SkillPullException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private List<SkillRequest> requests() throws MojoFailureException {
        return SkillRequests.create(skills == null ? List.of() : skills, sourceConfigurations(),
                new SkillRequests.Locations() {
                    @Override
                    public SkillSource maven(String sourceId, String groupId, String artifactId, String classifier) {
                        // an empty classifier is no valid template - {name[:0]} expands to it
                        return new MavenSkillSource(sourceId, new SourceTemplate("groupId", groupId),
                                new SourceTemplate("artifactId", artifactId),
                                new SourceTemplate("classifier", classifier.isEmpty() ? "{name[:0]}" : classifier),
                                repositorySystem(), repositorySession(), remoteRepositories());
                    }

                    @Override
                    public SkillSource oci(String sourceId, String registry, String repository,
                                           String registryServerId, boolean allowPlainHttp,
                                           List<File> caCertificates) throws MojoFailureException {
                        return OciSkillSource.image(sourceId, registry, repository,
                                registryClient(caCertificates, allowPlainHttp), credentials(registryServerId));
                    }
                });
    }
}
