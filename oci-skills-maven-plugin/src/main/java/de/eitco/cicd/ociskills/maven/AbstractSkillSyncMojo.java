package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillInstaller;
import de.eitco.cicd.ociskills.core.install.SkillLockFile;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.eclipse.aether.RepositorySystem;

import java.io.File;
import java.util.ArrayList;
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
     *         <id>cicd/nexus-test</id>
     *         <version>[1.0,2.0)</version>
     *     </skill>
     *     <skill>
     *         <id>cicd/release-notes</id>
     *         <version>2.1.0</version>
     *         <status>testing</status>
     *         <source>eitco-oci</source>
     *     </skill>
     * </skills>
     * }</pre>
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
        List<SkillRequest> requests = new ArrayList<>();
        for (SkillConfiguration skill : skills == null ? List.<SkillConfiguration>of() : skills) {
            try {
                SkillId id = SkillId.parse(skill.getId());
                String version = skill.getVersion();
                SkillVersions.toRange(version);
                SkillStatus status = skill.getStatus() == null || skill.getStatus().isBlank() ? SkillStatus.PUBLISHED
                        : SkillStatus.parse(skill.getStatus());
                String source = skill.getSource() == null || skill.getSource().isBlank() ? null
                        : skill.getSource().trim();
                requests.add(new SkillRequest(id, version.trim(), status, source));
            } catch (IllegalArgumentException e) {
                throw new MojoFailureException("invalid <skill> " + skill.getId() + ": " + e.getMessage(), e);
            }
        }
        return requests;
    }
}
