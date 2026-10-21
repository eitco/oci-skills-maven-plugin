package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.OciReferenceFactory;
import de.eitco.cicd.ociskills.core.registry.PushResult;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import de.eitco.cicd.ociskills.core.registry.SkillPublisher;
import de.eitco.cicd.ociskills.registry.oras.OrasRegistryClient;
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

/**
 * Pushes the OCI image layouts created by {@code oci-skills:build} - one per skill directory below {@code skillsDirectory}
 * - to an OCI registry as {@code <registry>/<repository>/<metadata.name>:<tag>}. All layouts are checked before the
 * first one is pushed. Credentials are taken from the {@code <server>} with the id
 * {@code registryServerId} of the maven settings - never from the POM.
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class SkillPushMojo extends AbstractSkillMojo {

    /**
     * The registry host, optionally with port, e.g. {@code nexus.eitco.de}.
     */
    @Parameter(property = "skill.registry")
    private String registry;

    /**
     * The repository path below which the skill is pushed, e.g. {@code docker-cicd-prerelease/eitco/cicd}. The skill
     * name is appended.
     */
    @Parameter(property = "skill.repository")
    private String repository;

    /**
     * The id of the {@code <server>} in the maven settings holding the registry credentials. Encrypted passwords are
     * supported. Without a server id the push is anonymous.
     */
    @Parameter(property = "skill.registryServerId")
    private String registryServerId;

    /**
     * Whether to compare the manifest digest confirmed by the registry with the local digest and fail on mismatch.
     */
    @Parameter(property = "skill.push.verifyDigest", defaultValue = "true")
    private boolean verifyDigest;

    /**
     * PEM files of additional certificate authorities to trust for the registry connection.
     */
    @Parameter
    private List<File> caCertificates;

    /**
     * A trust store with additional certificate authorities to trust for the registry connection.
     */
    @Parameter(property = "skill.push.trustStore")
    private File trustStore;

    /**
     * The password of {@code trustStore}.
     */
    @Parameter(property = "skill.push.trustStorePassword")
    private String trustStorePassword;

    /**
     * The type of {@code trustStore}, e.g. {@code PKCS12}. Defaults to the JVM default type.
     */
    @Parameter(property = "skill.push.trustStoreType")
    private String trustStoreType;

    /**
     * Whether to connect to the registry via plain http instead of https. Only meant for local test registries.
     */
    @Parameter(property = "skill.push.allowPlainHttp", defaultValue = "false")
    private boolean allowPlainHttp;

    /**
     * Skips the push. Use this to prevent publishing in local builds.
     */
    @Parameter(property = "skill.push.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    private final SettingsDecrypter settingsDecrypter;

    @Inject
    public SkillPushMojo(SettingsDecrypter settingsDecrypter) {
        this.settingsDecrypter = settingsDecrypter;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("skill.push.skip is set - skipping push");
            return;
        }
        List<SkillImage> images = new ArrayList<>();
        for (Path skill : skills()) {
            images.add(read(outputDirectory(skill)));
        }
        RegistryCredentials credentials = new RegistryCredentialsResolver(settingsDecrypter)
                .resolve(settings, registryServerId);
        RegistryConnection connection = new RegistryConnection(
                caCertificates == null ? List.of() : caCertificates.stream().map(File::toPath).toList(),
                trustStore == null ? null : trustStore.toPath(), trustStorePassword, trustStoreType, allowPlainHttp);
        if (allowPlainHttp) {
            getLog().warn("allowPlainHttp is enabled - pushing to " + registry + " without TLS");
        }
        SkillPublisher publisher = new SkillPublisher(new OrasRegistryClient(connection));
        for (SkillImage image : images) {
            push(publisher, image, credentials);
        }
    }

    private SkillImage read(Path layout) throws MojoFailureException {
        OciImageLayout image;
        try {
            image = new OciLayoutReader().read(layout);
        } catch (SkillBuildException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        String name = image.manifest().annotations() == null ? null
                : image.manifest().annotations().get(ImageManifestBuilder.ANNOTATION_TITLE);
        if (name == null || name.isBlank()) {
            throw new MojoFailureException("manifest in " + layout + " has no "
                    + ImageManifestBuilder.ANNOTATION_TITLE + " annotation - rebuild it with oci-skills:build");
        }
        try {
            return new SkillImage(image, new OciReferenceFactory().create(registry, repository, name,
                    image.tag()));
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("invalid push target: " + e.getMessage()
                    + " - configure <registry> and <repository> of the plugin", e);
        }
    }

    private void push(SkillPublisher publisher, SkillImage image, RegistryCredentials credentials)
            throws MojoExecutionException {
        getLog().info("Pushing " + image.image().manifestDescriptor().digest() + " to " + image.destination()
                + (credentials.isAnonymous() ? " (anonymous)" : " as " + credentials.username()));
        PushResult result;
        try {
            result = publisher.publish(image.image(), image.destination(), credentials, verifyDigest);
        } catch (SkillPushException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        for (OciDescriptor blob : result.uploadedBlobs()) {
            getLog().info("  uploaded " + blob.mediaType() + " " + blob.digest() + " (" + blob.size() + " bytes)");
        }
        for (OciDescriptor blob : result.skippedBlobs()) {
            getLog().info("  exists   " + blob.mediaType() + " " + blob.digest());
        }
        getLog().info("Pushed " + image.destination() + " - digest confirmed by registry: " + result.confirmedDigest()
                + (verifyDigest ? " (verified)" : " (not verified)"));
    }

    private record SkillImage(OciImageLayout image, OciReference destination) {
    }
}
