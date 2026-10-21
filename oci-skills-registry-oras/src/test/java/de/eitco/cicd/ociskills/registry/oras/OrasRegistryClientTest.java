package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillBuildRequest;
import de.eitco.cicd.ociskills.core.SkillImageBuilder;
import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.PushResult;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OrasRegistryClientTest {

    private static final RegistryConnection PLAIN_HTTP = new RegistryConnection(List.of(), null, null, null, true);

    @TempDir
    Path tempDirectory;

    private FakeOciRegistry registry;
    private OciImageLayout image;

    @BeforeEach
    void setUp() throws Exception {
        registry = FakeOciRegistry.http();
        image = buildImage(tempDirectory);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    static OciImageLayout buildImage(Path directory) throws Exception {
        Path skill = Files.createDirectories(directory.resolve("nexus-test"));
        Files.writeString(skill.resolve("SKILL.md"), """
                ---
                name: nexus-test
                description: Test skill für die OCI Registry
                ---

                # Nexus Test

                Publishes to an OCI registry.
                """);
        Path output = directory.resolve("skill-oci");
        new SkillImageBuilder().build(new SkillBuildRequest(skill, output, FileSelection.defaults(),
                SkillStatus.DRAFT, Instant.parse("2026-10-16T14:45:03Z"), new SkillCardSettings("cicd", "0.0.1"),
                directory.resolve("generated-skills/nexus-test")));
        return new OciLayoutReader().read(output);
    }

    private OciReference destination() {
        return new OciReference(registry.address(), "skills/nexus-test", "0.0.1-draft");
    }

    private PushResult push(RegistryConnection connection, RegistryCredentials credentials) throws Exception {
        return new OrasRegistryClient(connection).push(image, destination(), credentials);
    }

    private SkillPushException pushFailure(RegistryConnection connection, RegistryCredentials credentials) {
        return catchThrowableOfType(SkillPushException.class, () -> push(connection, credentials));
    }

    private List<OciDescriptor> blobs() {
        return List.of(image.manifest().config(), image.manifest().layers().get(0));
    }

    @Test
    void pushesBlobsAndManifestAndReturnsConfirmedDigest() throws Exception {
        PushResult result = push(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(result.destination()).isEqualTo(destination());
        assertThat(result.confirmedDigest()).isEqualTo(image.manifestDescriptor().digest());
        assertThat(result.uploadedBlobs()).containsExactlyInAnyOrderElementsOf(blobs());
        assertThat(result.skippedBlobs()).isEmpty();
        for (OciDescriptor blob : blobs()) {
            assertThat(registry.blobs().get(blob.digest().toString())).isEqualTo(Files.readAllBytes(image.blob(blob)));
        }
    }

    @Test
    void pushesManifestUnchangedUnderTag() throws Exception {
        push(PLAIN_HTTP, RegistryCredentials.anonymous());

        FakeOciRegistry.StoredManifest manifest = registry.manifest("skills/nexus-test", "0.0.1-draft");
        assertThat(manifest.content()).isEqualTo(Files.readAllBytes(image.blob(image.manifestDescriptor())));
        assertThat(manifest.contentType()).isEqualTo(MediaTypes.IMAGE_MANIFEST);
    }

    @Test
    void pushesManifestAsLastStep() throws Exception {
        push(PLAIN_HTTP, RegistryCredentials.anonymous());

        List<FakeOciRegistry.Request> writes = registry.requests().stream()
                .filter(request -> request.method().equals("POST") || request.method().equals("PUT"))
                .toList();
        assertThat(writes).last().satisfies(request -> {
            assertThat(request.method()).isEqualTo("PUT");
            assertThat(request.path()).isEqualTo("/v2/skills/nexus-test/manifests/0.0.1-draft");
        });
    }

    @Test
    void skipsExistingBlobs() throws Exception {
        for (OciDescriptor blob : blobs()) {
            registry.putBlob(blob.digest().toString(), Files.readAllBytes(image.blob(blob)));
        }

        PushResult result = push(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(result.uploadedBlobs()).isEmpty();
        assertThat(result.skippedBlobs()).containsExactlyInAnyOrderElementsOf(blobs());
        assertThat(registry.requests("POST")).isEmpty();
    }

    @Test
    void authenticatesWithCredentials() throws Exception {
        registry.requireCredentials("deployer", "s3cr3t");

        PushResult result = push(PLAIN_HTTP, new RegistryCredentials("deployer", "s3cr3t"));

        assertThat(result.confirmedDigest()).isEqualTo(image.manifestDescriptor().digest());
    }

    @Test
    void sendsNoCredentialsWhenAnonymous() throws Exception {
        push(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(registry.requests()).allSatisfy(request -> assertThat(request.authorization()).isNull());
    }

    @Test
    void reportsAuthenticationFailureWithoutRevealingPassword() {
        registry.requireCredentials("deployer", "s3cr3t");

        SkillPushException exception = pushFailure(PLAIN_HTTP, new RegistryCredentials("deployer", "wrong-p4ss"));

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.AUTHENTICATION_FAILED);
        assertThat(exception).hasMessageContaining(destination().toString())
                .hasMessageContaining("[push]");
        assertThat(fullMessages(exception)).doesNotContain("wrong-p4ss").doesNotContain("s3cr3t");
    }

    @Test
    void reportsMissingPermission() {
        registry.failOn("POST /v2/skills/nexus-test/blobs/uploads/.*", 403, "DENIED");

        SkillPushException exception = pushFailure(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.PERMISSION_DENIED);
        assertThat(exception).hasMessageContaining(MediaTypes.IMAGE_CONFIG);
    }

    @Test
    void reportsUnknownRepository() {
        registry.failOn("POST /v2/skills/nexus-test/blobs/uploads/.*", 404, "NAME_UNKNOWN");

        SkillPushException exception = pushFailure(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.REPOSITORY_NOT_FOUND);
    }

    @Test
    void reportsRejectedMediaType() {
        registry.failOn("PUT /v2/skills/nexus-test/manifests/.*", 400, "MANIFEST_INVALID");

        SkillPushException exception = pushFailure(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.MEDIA_TYPE_REJECTED);
        assertThat(exception).hasMessageContaining(MediaTypes.IMAGE_MANIFEST);
    }

    @Test
    void reportsUnsupportedMediaType() {
        registry.failOn("PUT /v2/skills/nexus-test/manifests/.*", 415, "UNSUPPORTED");

        assertThat(pushFailure(PLAIN_HTTP, RegistryCredentials.anonymous()).reason())
                .isEqualTo(SkillPushException.Reason.MEDIA_TYPE_REJECTED);
    }

    @Test
    void reportsUnreachableRegistry() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        OciReference unreachable = new OciReference("localhost:" + freePort, "skills/nexus-test", "0.0.1-draft");

        SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                () -> new OrasRegistryClient(PLAIN_HTTP).push(image, unreachable, RegistryCredentials.anonymous()));

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.REGISTRY_UNREACHABLE);
        assertThat(exception).hasMessageContaining(unreachable.toString());
    }

    @Test
    void usesTlsUnlessPlainHttpIsAllowed() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())) {
            java.util.concurrent.CompletableFuture<Integer> firstByte = new java.util.concurrent.CompletableFuture<>();
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try (java.net.Socket socket = server.accept()) {
                        firstByte.complete(socket.getInputStream().read());
                    } catch (java.io.IOException e) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            OciReference target = new OciReference("localhost:" + server.getLocalPort(), "skills/nexus-test",
                    "0.0.1-draft");

            SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                    () -> new OrasRegistryClient(RegistryConnection.defaults())
                            .push(image, target, RegistryCredentials.anonymous()));

            assertThat(exception).isNotNull();
            // 0x16 starts a TLS handshake record
            assertThat(firstByte.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(0x16);
        }
    }

    @Test
    void returnsDigestReportedByRegistry() throws Exception {
        String other = "sha256:" + "a".repeat(64);
        registry.confirmDigest(other);

        PushResult result = push(PLAIN_HTTP, RegistryCredentials.anonymous());

        assertThat(result.confirmedDigest()).hasToString(other);
    }

    static String fullMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.append(current).append('\n');
        }
        return messages.toString();
    }
}
