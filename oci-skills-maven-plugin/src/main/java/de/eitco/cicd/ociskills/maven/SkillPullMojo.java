package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillInstaller;
import de.eitco.cicd.ociskills.core.install.SkillOrigin;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Installs a single skill from the command line - without lock file. Exactly one of {@code skill},
 * {@code artifact} or {@code image} selects the skill:
 * <pre>
 * mvn oci-skills:pull -Dskill=cicd/nexus-test[:version]
 * mvn oci-skills:pull -Dskill.artifact=de.eitco.cicd:cicd-skills:1.2.0:nexus-test
 * mvn oci-skills:pull -Dskill.image=nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test:1.2.0
 * </pre>
 * {@code skill} is resolved from the configured {@code sources}, {@code artifact} from the maven repositories of the
 * build. The goal needs no project. Pulled skills are kept by {@code oci-skills:sync}.
 */
@Mojo(name = "pull", requiresProject = false, threadSafe = true)
public class SkillPullMojo extends AbstractSkillPullMojo {

    /**
     * The skill to resolve from the configured sources: {@code <namespace>/<name>[:<version>]}. The version may
     * be a range - without version the highest version is installed.
     */
    @Parameter(property = "skill")
    private String skill;

    /**
     * With {@code skill}: the least mature status accepted - {@code published}, {@code testing} or {@code draft}.
     */
    @Parameter(property = "skill.status", defaultValue = "published")
    private String status;

    /**
     * With {@code skill}: the id of the only source to ask.
     */
    @Parameter(property = "skill.source")
    private String source;

    /**
     * The skill artifact attached by {@code oci-skills:archive}:
     * {@code <groupId>:<artifactId>:<version>:<classifier>}.
     */
    @Parameter(property = "skill.artifact")
    private String artifact;

    /**
     * The skill image: {@code <registry>/<repository>:<tag>} or {@code <registry>/<repository>@<digest>}.
     */
    @Parameter(property = "skill.image")
    private String image;

    /**
     * With {@code image}: the id of the {@code <server>} in the maven settings holding the registry credentials.
     */
    @Parameter(property = "skill.registryServerId")
    private String registryServerId;

    /**
     * With {@code image}: whether to connect to the registry via plain http instead of https. Only meant for local
     * test registries.
     */
    @Parameter(property = "skill.pull.allowPlainHttp", defaultValue = "false")
    private boolean allowPlainHttp;

    @Inject
    public SkillPullMojo(RepositorySystem repositorySystem, SettingsDecrypter settingsDecrypter) {
        super(repositorySystem, settingsDecrypter);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        long selected = Stream.of(skill, artifact, image).filter(value -> value != null && !value.isBlank()).count();
        if (selected != 1) {
            throw new MojoFailureException("set exactly one of -Dskill=<namespace>/<name>[:<version>], "
                    + "-Dskill.artifact=<groupId>:<artifactId>:<version>:<classifier> or "
                    + "-Dskill.image=<registry>/<repository>:<tag>");
        }
        Path workDirectory;
        try {
            workDirectory = Files.createTempDirectory("oci-skills-pull-");
        } catch (IOException e) {
            throw new MojoExecutionException("creating a temporary directory failed: " + e, e);
        }
        try {
            Pulled pulled = skill != null && !skill.isBlank() ? pullSkill(workDirectory)
                    : artifact != null && !artifact.isBlank() ? pullArtifact(workDirectory)
                    : pullImage(workDirectory);
            SkillInstaller installer = new SkillInstaller(installDirectory());
            installer.install(pulled.image(), pulled.origin());
            installer.writeGitignore();
            getLog().info("Installed " + pulled.origin().id() + " " + pulled.origin().version() + " ("
                    + pulled.origin().status().value() + ") from " + pulled.origin().source() + " into "
                    + installDirectory().resolve(pulled.origin().id().name()));
        } catch (SkillPullException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            delete(workDirectory);
        }
    }

    private Pulled pullSkill(Path workDirectory) throws MojoFailureException, SkillPullException {
        String value = skill.trim();
        int separator = value.indexOf(':');
        String versionSpec = separator < 0 ? "[0,)" : value.substring(separator + 1);
        SkillId id;
        SkillStatus minimumStatus;
        try {
            id = SkillId.parse(separator < 0 ? value : value.substring(0, separator));
            SkillVersions.toRange(versionSpec);
            minimumStatus = SkillStatus.parse(status);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("invalid -Dskill=" + skill + ": " + e.getMessage(), e);
        }
        List<SkillSource> sources = sources();
        if (sources.isEmpty()) {
            throw new MojoFailureException("-Dskill needs <sources> in the plugin configuration - use "
                    + "-Dskill.artifact or -Dskill.image without project");
        }
        SkillResolver.Resolved resolved = new SkillResolver(sources).resolve(id, versionSpec, minimumStatus,
                source, workDirectory);
        return new Pulled(resolved.origin(), resolved.image());
    }

    private Pulled pullArtifact(Path workDirectory) throws MojoFailureException, SkillPullException {
        String[] parts = artifact.trim().split(":");
        if (parts.length != 4 || Stream.of(parts).anyMatch(String::isBlank)) {
            throw new MojoFailureException("invalid -Dskill.artifact=" + artifact
                    + " - expected <groupId>:<artifactId>:<version>:<classifier>");
        }
        DefaultArtifact coordinates = new DefaultArtifact(parts[0], parts[1], parts[3], MavenSkillSource.EXTENSION,
                parts[2]);
        OciImageLayout layout = MavenSkillSource.fetch(repositorySystem(), repositorySession(),
                remoteRepositories(), coordinates, workDirectory).orElseThrow(() -> new SkillPullException(
                coordinates + " was not found in the repositories of the build"));
        return new Pulled(origin(layout, coordinates.toString()), layout);
    }

    private Pulled pullImage(Path workDirectory) throws MojoFailureException, SkillPullException {
        OciReference reference;
        Digest digest = null;
        String value = image.trim();
        try {
            int at = value.indexOf('@');
            if (at >= 0) {
                digest = Digest.parse(value.substring(at + 1));
                value = value.substring(0, at);
            }
            int slash = value.indexOf('/');
            int colon = value.lastIndexOf(':');
            if (slash <= 0) {
                throw new IllegalArgumentException("no repository");
            }
            boolean tagged = colon > value.lastIndexOf('/');
            if (!tagged && digest == null) {
                throw new IllegalArgumentException("no tag or digest");
            }
            String repository = value.substring(slash + 1, tagged ? colon : value.length());
            reference = new OciReference(value.substring(0, slash), repository,
                    tagged ? value.substring(colon + 1) : "pulled");
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("invalid -Dskill.image=" + image + " - expected "
                    + "<registry>/<repository>:<tag> or <registry>/<repository>@<digest>: " + e.getMessage(), e);
        }
        OciImageLayout layout = registryClient(List.of(), allowPlainHttp)
                .pull(reference, digest, workDirectory, credentials(registryServerId))
                .orElseThrow(() -> new SkillPullException(image + " was not found"));
        return new Pulled(origin(layout, image.trim()), layout);
    }

    /**
     * @return the origin according to the annotations of the image
     */
    private static SkillOrigin origin(OciImageLayout layout, String source) throws SkillPullException {
        Map<String, String> annotations = Optional.ofNullable(layout.manifest().annotations()).orElse(Map.of());
        try {
            return new SkillOrigin(new SkillId(annotations.get(ImageManifestBuilder.ANNOTATION_VENDOR),
                    annotations.get(ImageManifestBuilder.ANNOTATION_TITLE)),
                    annotations.get(ImageManifestBuilder.ANNOTATION_VERSION),
                    SkillStatus.parse(annotations.get(SkillStatus.ANNOTATION)),
                    layout.manifestDescriptor().digest(), source);
        } catch (IllegalArgumentException e) {
            throw new SkillPullException(source + " is no skill image built by the plugin: " + e.getMessage(), e);
        }
    }

    private void delete(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            getLog().warn("deleting " + directory + " failed: " + e);
        }
    }

    private record Pulled(SkillOrigin origin, OciImageLayout image) {
    }
}
