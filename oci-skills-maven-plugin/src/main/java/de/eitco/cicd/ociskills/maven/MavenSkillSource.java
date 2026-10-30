package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.archive.SkillJarReader;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.oci.StatusSuffix;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.version.Version;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pulls skills from maven repositories: a skill is the artifact
 * {@code <groupId>:<artifactId>:jar:<classifier>:<version>}. Group id, artifact id and classifier are templates
 * derived from the skill id and its {@code <repository>}, the classifier is suffixed with the status unless the
 * status is {@code published} - by default it is the skill name, like {@code oci-skills:archive} attaches skills.
 * If the classifier expands to an empty value, the skill is the main artifact, which has to contain the skill in
 * the requested status. Uses the repositories, mirrors and credentials of the build - including the local
 * repository and the reactor.
 */
final class MavenSkillSource implements SkillSource {

    static final String EXTENSION = "jar";

    private final String id;
    private final SourceTemplate groupId;
    private final SourceTemplate artifactId;
    private final SourceTemplate classifier;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    MavenSkillSource(String id, SourceTemplate groupId, SourceTemplate artifactId, SourceTemplate classifier,
                     RepositorySystem repositorySystem, RepositorySystemSession session,
                     List<RemoteRepository> repositories) {
        this.id = id;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = List.copyOf(repositories);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> versions(SkillId skill, String repository, String versionSpec) throws SkillPullException {
        Artifact artifact = new DefaultArtifact(coordinate(groupId, skill, repository),
                coordinate(artifactId, skill, repository), EXTENSION, SkillVersions.toRange(versionSpec));
        try {
            return repositorySystem.resolveVersionRange(session,
                            new VersionRangeRequest(artifact, repositories, null))
                    .getVersions().stream()
                    .map(Version::toString)
                    .toList();
        } catch (VersionRangeResolutionException e) {
            throw new SkillPullException("resolving the versions " + versionSpec + " of " + skill + " from "
                    + artifact.getGroupId() + ":" + artifact.getArtifactId() + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<SkillImage> fetch(SkillId skill, String repository, String version, SkillStatus status,
                                      Digest digest, Path directory) throws SkillPullException {
        Artifact artifact = artifact(skill, repository, version, status);
        Optional<SkillImage> image = fetch(repositorySystem, session, repositories, artifact, directory);
        // the main artifact has no status in its coordinates - it may contain the skill in another status
        return artifact.getClassifier().isEmpty() ? image.filter(found -> hasStatus(found, status)) : image;
    }

    /**
     * @return the coordinates of the skill - without classifier if the classifier expands to an empty value
     */
    Artifact artifact(SkillId skill, String repository, String version, SkillStatus status)
            throws SkillPullException {
        String expandedClassifier = expand(classifier, skill, repository);
        return new DefaultArtifact(coordinate(groupId, skill, repository), coordinate(artifactId, skill, repository),
                expandedClassifier.isEmpty() ? "" : StatusSuffix.append(expandedClassifier, status), EXTENSION,
                version);
    }

    static boolean hasStatus(SkillImage image, SkillStatus status) {
        Map<String, String> annotations = Optional.ofNullable(image.manifest().annotations()).orElse(Map.of());
        return status.value().equals(annotations.get(SkillStatus.ANNOTATION));
    }

    private String coordinate(SourceTemplate template, SkillId skill, String repository) throws SkillPullException {
        String value = expand(template, skill, repository);
        if (value.isEmpty()) {
            throw new SkillPullException("the source " + id + " cannot locate the skill: " + template
                    + " is empty for " + skill);
        }
        return value;
    }

    private String expand(SourceTemplate template, SkillId skill, String repository) throws SkillPullException {
        try {
            return template.expand(skill, repository);
        } catch (IllegalArgumentException e) {
            throw new SkillPullException("the source " + id + " cannot locate the skill: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a skill jar attached by {@code oci-skills:archive}, extracts and verifies the skill it contains.
     *
     * @return the skill - empty if the artifact does not exist
     */
    static Optional<SkillImage> fetch(RepositorySystem repositorySystem, RepositorySystemSession session,
                                      List<RemoteRepository> repositories, Artifact artifact, Path directory)
            throws SkillPullException {
        ArtifactResult result;
        try {
            result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, repositories, null));
        } catch (ArtifactResolutionException e) {
            if (e.getResult() != null && e.getResult().isMissing()) {
                return Optional.empty();
            }
            throw new SkillPullException("resolving " + artifact + " failed: " + e.getMessage(), e);
        }
        Path file = result.getArtifact().getFile().toPath();
        try {
            return Optional.of(new SkillJarReader().read(file, directory));
        } catch (SkillBuildException e) {
            throw new SkillPullException(artifact + " (" + file + ") is no valid skill jar: " + e.getMessage(), e);
        }
    }
}
