package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillGitException;
import de.eitco.cicd.ociskills.core.git.GitCredentials;
import de.eitco.cicd.ociskills.core.git.GitPublishRequest;
import de.eitco.cicd.ociskills.core.git.GitPublishResult;
import de.eitco.cicd.ociskills.core.git.GitSkill;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import de.eitco.cicd.ociskills.git.jgit.JGitSkillRepositoryPublisher;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecrypter;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Publishes the skills built by {@code oci-skills:build} to a git repository, so they can be installed with
 * {@code npx skills add <gitUrl>#<gitTag>}. The directories {@code <gitDirectory>/<skill>} are replaced with the
 * files of the images, everything else in the repository is kept - several projects can publish into one repository.
 * <p>
 * Releases are committed to {@code gitBranch} and tagged with {@code gitTag}. An existing tag is kept with a warning
 * and the skills are published to the branch anyway, so release builds can be repeated. Snapshots are only published
 * to {@code gitSnapshotBranch} - without tag - if it is configured. Without {@code gitUrl} the goal does nothing.
 * Credentials are taken from the {@code <server>} with the id {@code gitServerId} of the maven settings - never from
 * the POM.
 */
@Mojo(name = "git-deploy", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class SkillGitDeployMojo extends AbstractSkillMojo {

    /**
     * The url of the repository to publish to, e.g. {@code https://git.example.com/ai/skills.git}. Only http(s) and
     * file urls are supported. Without url the goal does nothing.
     */
    @Parameter(property = "skill.git.url")
    private String gitUrl;

    /**
     * The id of the {@code <server>} in the maven settings holding user name and password or access token for the
     * repository. Encrypted passwords are supported. Without a server id the repository is accessed anonymously.
     */
    @Parameter(property = "skill.git.serverId")
    private String gitServerId;

    /**
     * The branch releases are committed to - created if missing.
     */
    @Parameter(property = "skill.git.branch", defaultValue = "main", required = true)
    private String gitBranch;

    /**
     * The branch snapshots are committed to, without tag. Without snapshot branch snapshots are not published.
     */
    @Parameter(property = "skill.git.snapshotBranch")
    private String gitSnapshotBranch;

    /**
     * The directory in the repository containing the skills - {@code npx skills} looks into {@code skills} by default.
     */
    @Parameter(property = "skill.git.directory", defaultValue = "skills", required = true)
    private String gitDirectory;

    /**
     * The tag of a release. It has to be unique within the repository - keep the artifact id in it, if several projects
     * publish into the same repository. Empty to create no tag.
     */
    @Parameter(property = "skill.git.tag", defaultValue = "${project.artifactId}-${project.version}")
    private String gitTag;

    /**
     * The first line of the commit message and the message of the tag. Name and digest of every published image are
     * appended to the commit message.
     */
    @Parameter(property = "skill.git.commitMessage",
            defaultValue = "release ${project.artifactId} ${project.version}", required = true)
    private String gitCommitMessage;

    /**
     * The statuses of the skills to publish - {@code published} by default. Skills with other statuses are skipped.
     */
    @Parameter
    private List<String> gitStatuses;

    /**
     * The name of the author and committer of the commits and the tagger of the tags.
     */
    @Parameter(property = "skill.git.authorName", defaultValue = "oci-skills-maven-plugin", required = true)
    private String gitAuthorName;

    /**
     * The email of the author and committer of the commits and the tagger of the tags.
     */
    @Parameter(property = "skill.git.authorEmail", defaultValue = "oci-skills-maven-plugin@noreply.invalid",
            required = true)
    private String gitAuthorEmail;

    /**
     * How often to try if the push is rejected because another build pushed to the branch in the meantime.
     */
    @Parameter(property = "skill.git.attempts", defaultValue = "3", required = true)
    private int gitAttempts;

    /**
     * The directory the repository is cloned into - deleted before cloning.
     */
    @Parameter(property = "skill.git.workDirectory", defaultValue = "${project.build.directory}/skill-git",
            required = true)
    private File gitWorkDirectory;

    /**
     * Skips publishing to git. Independent of {@code skill.push.skip}, so projects can publish to git only.
     */
    @Parameter(property = "skill.git.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    private final SettingsDecrypter settingsDecrypter;

    @Inject
    public SkillGitDeployMojo(SettingsDecrypter settingsDecrypter) {
        this.settingsDecrypter = settingsDecrypter;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (gitUrl == null || gitUrl.isBlank()) {
            getLog().debug("no gitUrl configured - skipping git deployment");
            return;
        }
        if (skip) {
            getLog().info("skill.git.skip is set - skipping git deployment");
            return;
        }
        Set<SkillStatus> statuses;
        try {
            statuses = GitDeployment.statuses(gitStatuses);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("invalid gitStatuses: " + e.getMessage(), e);
        }
        List<OciImageLayout> all = new ArrayList<>();
        for (Path skill : skills()) {
            all.add(read(outputDirectory(skill)));
        }
        List<OciImageLayout> images = GitDeployment.select(all, statuses);
        if (images.isEmpty()) {
            getLog().info("no skill has one of the statuses " + statuses.stream().map(SkillStatus::value)
                    .collect(Collectors.joining(", ")) + " - skipping git deployment");
            return;
        }
        String version = GitDeployment.version(images.get(0));
        Optional<GitDeployment.Target> target = GitDeployment.target(version == null ? "" : version, gitBranch,
                gitSnapshotBranch, gitTag);
        if (target.isEmpty()) {
            getLog().info(version + " is a snapshot and no gitSnapshotBranch is configured - skipping git deployment");
            return;
        }
        RegistryCredentials credentials = new RegistryCredentialsResolver(settingsDecrypter)
                .resolve(settings, gitServerId);
        List<GitSkill> skills = images.stream().map(image -> new GitSkill(GitDeployment.name(image), image))
                .toList();
        GitPublishRequest request = new GitPublishRequest(gitUrl, target.get().branch(), gitDirectory, skills,
                target.get().tag(), GitDeployment.message(gitCommitMessage, images), gitAuthorName, gitAuthorEmail,
                credentials.isAnonymous() ? GitCredentials.anonymous()
                        : new GitCredentials(credentials.username(), credentials.password()),
                gitWorkDirectory.toPath(), gitAttempts);
        getLog().info("Publishing " + skills.stream().map(GitSkill::name).collect(Collectors.joining(", "))
                + " to " + target.get().branch() + " of " + gitUrl
                + (credentials.isAnonymous() ? " (anonymous)" : " as " + credentials.username()));
        GitPublishResult result;
        try {
            result = new JGitSkillRepositoryPublisher().publish(request);
        } catch (SkillGitException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        report(request, result);
    }

    private void report(GitPublishRequest request, GitPublishResult result) {
        getLog().info(result.changed() ? "Committed " + result.commit() + " to " + request.branch()
                : "The skills are unchanged - " + request.branch() + " stays at " + result.commit());
        switch (result.tag()) {
            case CREATED -> getLog().info("Tagged " + result.commit() + " as " + request.tag());
            case EXISTED -> getLog().warn("The tag " + request.tag() + " exists already and points to "
                    + result.tagCommit() + " - it is left unchanged"
                    + (result.tagCommit().equals(result.commit()) ? ""
                    : ", the skills were published to " + request.branch() + " as " + result.commit() + " anyway"));
            case NONE -> {
            }
        }
    }

    private OciImageLayout read(Path layout) throws MojoFailureException {
        OciImageLayout image;
        try {
            image = new OciLayoutReader().read(layout);
        } catch (SkillBuildException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        String name = GitDeployment.name(image);
        if (name == null || name.isBlank()) {
            throw new MojoFailureException("manifest in " + layout + " has no "
                    + ImageManifestBuilder.ANNOTATION_TITLE + " annotation - rebuild it with oci-skills:build");
        }
        return image;
    }
}
