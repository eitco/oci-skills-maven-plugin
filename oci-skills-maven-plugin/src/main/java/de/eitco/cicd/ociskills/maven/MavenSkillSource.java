package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layer.TarExtractor;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Pulls skills from maven repositories - like {@code oci-skills:archive} attaches them: a skill is the artifact
 * {@code <groupId>:<artifactId>:tar:<classifier>:<version>}, the classifier is the skill name suffixed with the
 * status unless the status is {@code published}. Uses the repositories, mirrors and credentials of the build -
 * including the local repository and the reactor.
 */
final class MavenSkillSource implements SkillSource {

    static final String EXTENSION = "tar";

    private final String id;
    private final SourceTemplate groupId;
    private final SourceTemplate artifactId;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    MavenSkillSource(String id, SourceTemplate groupId, SourceTemplate artifactId, RepositorySystem repositorySystem,
                     RepositorySystemSession session, List<RemoteRepository> repositories) {
        this.id = id;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = List.copyOf(repositories);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> versions(SkillId skill, String versionSpec) throws SkillPullException {
        Artifact artifact = new DefaultArtifact(groupId.expand(skill), artifactId.expand(skill), EXTENSION,
                SkillVersions.toRange(versionSpec));
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
    public Optional<OciImageLayout> fetch(SkillId skill, String version, SkillStatus status, Digest digest,
                                          Path directory) throws SkillPullException {
        return fetch(repositorySystem, session, repositories, new DefaultArtifact(groupId.expand(skill),
                artifactId.expand(skill), StatusSuffix.append(skill.name(), status), EXTENSION, version), directory);
    }

    /**
     * Resolves an artifact attached by {@code oci-skills:archive} and extracts the image layout it contains.
     *
     * @return the layout - empty if the artifact does not exist
     */
    static Optional<OciImageLayout> fetch(RepositorySystem repositorySystem, RepositorySystemSession session,
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
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            new TarExtractor().extract(in, directory);
            return Optional.of(new OciLayoutReader().read(directory));
        } catch (IOException | SkillBuildException e) {
            throw new SkillPullException(artifact + " (" + file + ") is no skill image archive: " + e.getMessage(),
                    e);
        }
    }
}
