package de.eitco.cicd.ociskills.core.oci;

import de.eitco.cicd.ociskills.core.GoldenMaster;
import de.eitco.cicd.ociskills.core.skillcard.SkillCard;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import de.eitco.cicd.ociskills.core.skillcard.SkillFrontmatterReader;
import de.eitco.cicd.ociskills.core.skillcard.SkillMetadata;
import de.eitco.cicd.ociskills.core.skillcard.SkillSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ImageManifestBuilderTest {

    private static final OciDescriptor CONFIG =
            new OciDescriptor(MediaTypes.IMAGE_CONFIG, GoldenMaster.CONFIG_DIGEST, GoldenMaster.CONFIG_SIZE);
    private static final OciDescriptor LAYER =
            new OciDescriptor(MediaTypes.IMAGE_LAYER_TAR_GZIP, GoldenMaster.LAYER_DIGEST, GoldenMaster.LAYER_SIZE);
    private static final SkillCard CARD = new SkillCard("skillimage.io/v1alpha1", "SkillCard",
            new SkillMetadata("nexus-test", "cicd", "0.0.1", "Test skill for OCI registry"), new SkillSpec("SKILL.md"));

    private final ImageManifestBuilder builder = new ImageManifestBuilder();

    @Test
    void createsManifestWithConfigAndSingleLayer() {
        OciImageManifest manifest = builder.build(CONFIG, LAYER, CARD, SkillStatus.DRAFT, 11, GoldenMaster.CREATED);

        assertThat(manifest.schemaVersion()).isEqualTo(2);
        assertThat(manifest.mediaType()).isEqualTo(MediaTypes.IMAGE_MANIFEST);
        assertThat(manifest.config()).isEqualTo(CONFIG);
        assertThat(manifest.layers()).containsExactly(LAYER);
    }

    @Test
    void mapsSkillCardToAnnotations() {
        OciImageManifest manifest = builder.build(CONFIG, LAYER, CARD, SkillStatus.TESTING, 11,
                Instant.parse("2026-10-16T14:45:03.123456Z"));

        assertThat(manifest.annotations()).containsOnly(
                entry("io.skillimage.status", "testing"),
                entry("io.skillimage.wordcount", "11"),
                entry("org.opencontainers.image.created", "2026-10-16T14:45:03Z"),
                entry("org.opencontainers.image.description", "Test skill for OCI registry"),
                entry("org.opencontainers.image.title", "nexus-test"),
                entry("org.opencontainers.image.vendor", "cicd"),
                entry("org.opencontainers.image.version", "0.0.1"));
    }

    @Test
    void usesEmptyDescriptionIfMissing() {
        SkillCard card = new SkillCard("skillimage.io/v1alpha1", "SkillCard",
                new SkillMetadata("nexus-test", "cicd", "0.0.1", null), new SkillSpec("SKILL.md"));

        OciImageManifest manifest = builder.build(CONFIG, LAYER, card, SkillStatus.DRAFT, 11, GoldenMaster.CREATED);

        assertThat(manifest.annotations()).contains(entry("org.opencontainers.image.description", ""));
    }

    @Test
    void omitsWordCountIfZeroLikeSkillctl() {
        OciImageManifest manifest = builder.build(CONFIG, LAYER, CARD, SkillStatus.DRAFT, 0, GoldenMaster.CREATED);

        assertThat(manifest.annotations()).doesNotContainKey("io.skillimage.wordcount");
    }

    @Test
    void serializesByteIdenticalToSkillctl() throws Exception {
        SkillCard card = SkillCard.of(new SkillFrontmatterReader().read(GoldenMaster.SOURCE).frontmatter(),
                new SkillCardSettings("cicd", "0.0.1"));
        int wordCount = WordCounter.countWords(Files.readString(GoldenMaster.SOURCE.resolve("SKILL.md")));

        OciImageManifest manifest = builder.build(CONFIG, LAYER, card, SkillStatus.DRAFT, wordCount,
                GoldenMaster.CREATED);
        SerializedBlob blob = OciJson.serializeBlob(manifest, MediaTypes.IMAGE_MANIFEST);

        assertThat(blob.bytes()).isEqualTo(Files.readAllBytes(GoldenMaster.blob(GoldenMaster.MANIFEST_DIGEST)));
        assertThat(blob.descriptor().digest()).isEqualTo(GoldenMaster.MANIFEST_DIGEST);
        assertThat(blob.descriptor().size()).isEqualTo(GoldenMaster.MANIFEST_SIZE);
    }

    @Test
    void doesNotAddRefNameToManifest() {
        OciImageManifest manifest = builder.build(CONFIG, LAYER, CARD, SkillStatus.DRAFT, 11, GoldenMaster.CREATED);

        assertThat(manifest.annotations()).doesNotContainKey(ImageManifestBuilder.ANNOTATION_REF_NAME);
        assertThat(manifest.layers()).hasSize(1).isEqualTo(List.of(LAYER));
    }
}
