package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrasRegistryClientPullTest {

    private static final RegistryConnection PLAIN_HTTP = new RegistryConnection(List.of(), null, null, null, true);
    private static final Digest OTHER_DIGEST = Digest.parse("sha256:" + "0".repeat(64));

    @TempDir
    Path tempDirectory;

    private FakeOciRegistry registry;
    private OciImageLayout image;
    private OrasRegistryClient client;

    @BeforeEach
    void setUp() throws Exception {
        registry = FakeOciRegistry.http();
        image = OrasRegistryClientTest.buildImage(tempDirectory.resolve("build"));
        client = new OrasRegistryClient(PLAIN_HTTP);
        client.push(image, reference("0.0.1-draft"), RegistryCredentials.anonymous());
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    private OciReference reference(String tag) {
        return new OciReference(registry.address(), "skills/nexus-test", tag);
    }

    private Path pulled() {
        return tempDirectory.resolve("pulled");
    }

    @Test
    void pullsTheImageTheTagPointsTo() throws Exception {
        Optional<OciImageLayout> layout = client.pull(reference("0.0.1-draft"), null, pulled(),
                RegistryCredentials.anonymous());

        assertThat(layout).hasValueSatisfying(pulled -> {
            assertThat(pulled.directory()).isEqualTo(pulled());
            assertThat(pulled.tag()).isEqualTo("0.0.1-draft");
            assertThat(pulled.manifestDescriptor()).isEqualTo(image.manifestDescriptor());
            assertThat(pulled.manifest()).isEqualTo(image.manifest());
        });
        for (OciDescriptor blob : List.of(image.manifest().config(), image.manifest().layers().get(0))) {
            assertThat(layout.get().blob(blob)).hasSameBinaryContentAs(image.blob(blob));
        }
    }

    @Test
    void pullsByDigest() throws Exception {
        Optional<OciImageLayout> layout = client.pull(reference("0.0.1-draft"),
                image.manifestDescriptor().digest(), pulled(), RegistryCredentials.anonymous());

        assertThat(layout).hasValueSatisfying(pulled ->
                assertThat(pulled.manifestDescriptor()).isEqualTo(image.manifestDescriptor()));
        assertThat(registry.requests("GET")).anySatisfy(request -> assertThat(request.path())
                .isEqualTo("/v2/skills/nexus-test/manifests/" + image.manifestDescriptor().digest()));
    }

    @Test
    void returnsNothingForAMissingImage() throws Exception {
        assertThat(client.pull(reference("9.9.9"), null, pulled(), RegistryCredentials.anonymous())).isEmpty();
        assertThat(client.pull(reference("0.0.1-draft"), OTHER_DIGEST, pulled(), RegistryCredentials.anonymous()))
                .isEmpty();
        assertThat(client.pull(new OciReference(registry.address(), "skills/missing", "0.0.1"), null, pulled(),
                RegistryCredentials.anonymous())).isEmpty();
    }

    @Test
    void rejectsAManifestNotMatchingTheDigest() throws Exception {
        byte[] tampered = new String(Files.readAllBytes(image.blob(image.manifestDescriptor())), UTF_8)
                .replace("nexus-test", "tampered-x").getBytes(UTF_8);
        registry.putManifest("skills/nexus-test", image.manifestDescriptor().digest().toString(), tampered,
                MediaTypes.IMAGE_MANIFEST);

        assertThatThrownBy(() -> client.pull(reference("0.0.1-draft"), image.manifestDescriptor().digest(),
                pulled(), RegistryCredentials.anonymous()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining(image.manifestDescriptor().digest().toString());
    }

    @Test
    void rejectsBlobsNotMatchingTheirDigest() throws Exception {
        registry.putBlob(image.manifest().layers().get(0).digest().toString(), "tampered".getBytes(UTF_8));

        assertThatThrownBy(() -> client.pull(reference("0.0.1-draft"), null, pulled(),
                RegistryCredentials.anonymous()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining(image.manifest().layers().get(0).digest().toString());
    }

    @Test
    void authenticatesWithCredentials() throws Exception {
        registry.requireCredentials("reader", "s3cr3t");

        assertThat(client.pull(reference("0.0.1-draft"), null, pulled(), new RegistryCredentials("reader", "s3cr3t")))
                .isPresent();
        assertThat(client.tags(reference(null), new RegistryCredentials("reader", "s3cr3t")))
                .containsExactly("0.0.1-draft");
        assertThatThrownBy(() -> client.tags(reference(null), new RegistryCredentials("reader", "wrong")))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("authentication failed")
                .hasMessageNotContaining("wrong");
    }

    @Test
    void listsTheTagsOfARepository() throws Exception {
        client.push(image, reference("0.0.2"), RegistryCredentials.anonymous());

        assertThat(client.tags(reference(null), RegistryCredentials.anonymous()))
                .containsExactlyInAnyOrder("0.0.1-draft", "0.0.2");
    }

    @Test
    void listsNoTagsOfAMissingRepository() throws Exception {
        assertThat(client.tags(new OciReference(registry.address(), "skills/missing", null),
                RegistryCredentials.anonymous())).isEmpty();
    }
}
