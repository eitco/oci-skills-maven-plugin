package de.eitco.cicd.ociskills.core.layout;

import de.eitco.cicd.ociskills.core.GoldenMaster;
import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;
import de.eitco.cicd.ociskills.core.oci.OciJson;
import de.eitco.cicd.ociskills.core.oci.SerializedBlob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OciLayoutTest {

    @TempDir
    Path layoutDirectory;

    private static Digest sha256(byte[] bytes) throws Exception {
        return Digest.sha256(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * Writes a minimal but consistent layout and returns the manifest descriptor.
     */
    private OciDescriptor writeLayout(OciLayoutWriter writer) throws Exception {
        writer.prepare();
        byte[] layer = "layer".getBytes(UTF_8);
        byte[] config = "{}".getBytes(UTF_8);
        writer.writeBlob(layer, sha256(layer));
        writer.writeBlob(config, sha256(config));
        OciImageManifest manifest = new OciImageManifest(2, MediaTypes.IMAGE_MANIFEST,
                new OciDescriptor(MediaTypes.IMAGE_CONFIG, sha256(config), config.length),
                List.of(new OciDescriptor(MediaTypes.IMAGE_LAYER_TAR_GZIP, sha256(layer), layer.length)),
                Map.of("org.opencontainers.image.version", "0.0.1"));
        SerializedBlob manifestBlob = OciJson.serializeBlob(manifest, MediaTypes.IMAGE_MANIFEST);
        writer.writeBlob(manifestBlob.bytes(), manifestBlob.descriptor().digest());
        writer.writeIndex(manifestBlob.descriptor(), "0.0.1-draft");
        return manifestBlob.descriptor();
    }

    @Test
    void writesOciLayoutFile() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));

        assertThat(Files.readString(layoutDirectory.resolve("oci-layout")))
                .isEqualTo("{\"imageLayoutVersion\":\"1.0.0\"}");
    }

    @Test
    void writesIndexReferencingManifestWithTag() throws Exception {
        OciDescriptor manifest = writeLayout(new OciLayoutWriter(layoutDirectory));

        assertThat(Files.readString(layoutDirectory.resolve("index.json"))).isEqualTo(
                "{\"schemaVersion\":2,\"mediaType\":\"application/vnd.oci.image.index.v1+json\",\"manifests\":["
                        + "{\"mediaType\":\"application/vnd.oci.image.manifest.v1+json\",\"digest\":\""
                        + manifest.digest() + "\",\"size\":" + manifest.size() + ","
                        + "\"annotations\":{\"org.opencontainers.image.ref.name\":\"0.0.1-draft\"}}]}");
    }

    @Test
    void storesBlobsContentAddressed() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));

        try (var blobs = Files.list(layoutDirectory.resolve("blobs/sha256"))) {
            assertThat(blobs.toList()).hasSize(3).allSatisfy(blob ->
                    assertThat(sha256(Files.readAllBytes(blob)).hex()).isEqualTo(blob.getFileName().toString()));
        }
    }

    @Test
    void movesTemporaryBlobs() throws Exception {
        OciLayoutWriter writer = new OciLayoutWriter(layoutDirectory);
        writer.prepare();
        Path temporary = writer.newTemporaryBlobFile();
        Files.writeString(temporary, "content");
        Digest digest = sha256("content".getBytes(UTF_8));

        writer.moveBlob(temporary, digest);

        assertThat(temporary).doesNotExist();
        assertThat(layoutDirectory.resolve("blobs/sha256").resolve(digest.hex())).hasContent("content");
    }

    @Test
    void prepareRemovesPreviousLayoutButKeepsOtherFiles() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));
        Files.writeString(layoutDirectory.resolve("blobs/sha256/orphan"), "orphan");
        Files.writeString(layoutDirectory.resolve("README.txt"), "keep me");

        new OciLayoutWriter(layoutDirectory).prepare();

        assertThat(layoutDirectory.resolve("index.json")).doesNotExist();
        assertThat(layoutDirectory.resolve("oci-layout")).doesNotExist();
        assertThat(layoutDirectory.resolve("blobs/sha256")).isEmptyDirectory();
        assertThat(layoutDirectory.resolve("README.txt")).hasContent("keep me");
    }

    @Test
    void readsWrittenLayout() throws Exception {
        OciDescriptor manifest = writeLayout(new OciLayoutWriter(layoutDirectory));

        OciImageLayout layout = new OciLayoutReader().read(layoutDirectory);

        assertThat(layout.tag()).isEqualTo("0.0.1-draft");
        assertThat(layout.manifestDescriptor().digest()).isEqualTo(manifest.digest());
        assertThat(layout.manifestDescriptor().size()).isEqualTo(manifest.size());
        assertThat(layout.manifest().layers()).hasSize(1);
        assertThat(layout.blob(layout.manifest().config())).isRegularFile();
    }

    @Test
    void readsSkillctlLayout() throws Exception {
        OciImageLayout layout = new OciLayoutReader().read(GoldenMaster.LAYOUT);

        assertThat(layout.manifestDescriptor().digest()).isEqualTo(GoldenMaster.MANIFEST_DIGEST);
        assertThat(layout.tag()).isEqualTo("cicd/nexus-test:0.0.1-draft");
        assertThat(layout.manifest().config().digest()).isEqualTo(GoldenMaster.CONFIG_DIGEST);
    }

    @Test
    void failsIfLayoutIsMissing() {
        assertThatThrownBy(() -> new OciLayoutReader().read(layoutDirectory.resolve("missing")))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("oci-skills:build");
    }

    @Test
    void failsIfBlobIsMissing() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));
        OciImageLayout layout = new OciLayoutReader().read(layoutDirectory);
        Files.delete(layout.blob(layout.manifest().layers().get(0)));

        assertThatThrownBy(() -> new OciLayoutReader().read(layoutDirectory))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining(layout.manifest().layers().get(0).digest().toString())
                .hasMessageContaining(MediaTypes.IMAGE_LAYER_TAR_GZIP);
    }

    @Test
    void failsIfBlobContentDoesNotMatchDigest() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));
        OciImageLayout layout = new OciLayoutReader().read(layoutDirectory);
        Files.writeString(layout.blob(layout.manifest().config()), "[]");

        assertThatThrownBy(() -> new OciLayoutReader().read(layoutDirectory))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining(layout.manifest().config().digest().toString())
                .hasMessageContaining("does not match");
    }

    @Test
    void failsIfBlobSizeDoesNotMatch() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));
        OciImageLayout layout = new OciLayoutReader().read(layoutDirectory);
        Files.writeString(layout.blob(layout.manifest().config()), "{}  ");

        assertThatThrownBy(() -> new OciLayoutReader().read(layoutDirectory))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("size");
    }

    @Test
    void failsIfIndexDoesNotContainExactlyOneManifest() throws Exception {
        writeLayout(new OciLayoutWriter(layoutDirectory));
        Files.writeString(layoutDirectory.resolve("index.json"),
                "{\"schemaVersion\":2,\"manifests\":[]}");

        assertThatThrownBy(() -> new OciLayoutReader().read(layoutDirectory))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("exactly one manifest");
    }
}
