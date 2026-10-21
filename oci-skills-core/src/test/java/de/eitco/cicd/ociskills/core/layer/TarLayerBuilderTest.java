package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.TarEntries;
import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarLayerBuilderTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-16T14:45:03Z");

    @TempDir
    Path tempDirectory;

    private Path skillDirectory;
    private List<SkillFile> files;

    private final TarLayerBuilder builder = new TarLayerBuilder();

    @BeforeEach
    void createFiles() throws Exception {
        skillDirectory = Files.createDirectory(tempDirectory.resolve("skill"));
        Path skillMd = TestSkills.write(skillDirectory, "SKILL.md", "# skill");
        Path script = TestSkills.write(skillDirectory, "scripts/run.sh", "#!/bin/sh");
        files = List.of(
                SkillFile.file("SKILL.md", skillMd, false),
                SkillFile.directory("scripts/", skillDirectory.resolve("scripts")),
                SkillFile.file("scripts/run.sh", script, true));
    }

    private static Digest sha256(byte[] bytes) throws Exception {
        return Digest.sha256(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void writesAllFilesIntoASingleTarWithNormalizedMetadata() throws Exception {
        LayerBlob blob = builder.build(files, TIMESTAMP, tempDirectory.resolve("layer.tar.gz"));

        assertThat(TarEntries.read(blob.file())).containsExactly(
                new TarEntries.Entry("SKILL.md", false, 0644, 0, 0, "", "", TIMESTAMP.toEpochMilli(), "# skill"),
                new TarEntries.Entry("scripts/", true, 0755, 0, 0, "", "", TIMESTAMP.toEpochMilli(), null),
                new TarEntries.Entry("scripts/run.sh", false, 0755, 0, 0, "", "", TIMESTAMP.toEpochMilli(),
                        "#!/bin/sh"));
    }

    @Test
    void sortsEntriesByPath() throws Exception {
        LayerBlob blob = builder.build(List.of(files.get(2), files.get(0), files.get(1)), TIMESTAMP,
                tempDirectory.resolve("layer.tar.gz"));

        assertThat(TarEntries.read(blob.file())).extracting(TarEntries.Entry::name)
                .containsExactly("SKILL.md", "scripts/", "scripts/run.sh");
    }

    @Test
    void computesDiffIdOfUncompressedTar() throws Exception {
        LayerBlob blob = builder.build(files, TIMESTAMP, tempDirectory.resolve("layer.tar.gz"));

        byte[] uncompressed;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(blob.file()))) {
            uncompressed = in.readAllBytes();
        }
        assertThat(blob.diffId()).isEqualTo(sha256(uncompressed));
    }

    @Test
    void computesDescriptorOfCompressedLayer() throws Exception {
        LayerBlob blob = builder.build(files, TIMESTAMP, tempDirectory.resolve("layer.tar.gz"));

        byte[] compressed = Files.readAllBytes(blob.file());
        assertThat(blob.descriptor().mediaType()).isEqualTo(MediaTypes.IMAGE_LAYER_TAR_GZIP);
        assertThat(blob.descriptor().digest()).isEqualTo(sha256(compressed));
        assertThat(blob.descriptor().size()).isEqualTo(compressed.length);
    }

    @Test
    void writesGzipHeaderWithoutTimestamp() throws Exception {
        LayerBlob blob = builder.build(files, TIMESTAMP, tempDirectory.resolve("layer.tar.gz"));

        byte[] header = new byte[10];
        try (InputStream in = Files.newInputStream(blob.file())) {
            assertThat(in.readNBytes(header, 0, 10)).isEqualTo(10);
        }
        assertThat(header[0]).isEqualTo((byte) 0x1f);
        assertThat(header[1]).isEqualTo((byte) 0x8b);
        assertThat(new byte[]{header[4], header[5], header[6], header[7]}).containsOnly(0);
    }

    @Test
    void isReproducible() throws Exception {
        LayerBlob first = builder.build(files, TIMESTAMP, tempDirectory.resolve("first.tar.gz"));
        Files.setLastModifiedTime(files.get(0).source(), java.nio.file.attribute.FileTime.from(Instant.now()));
        LayerBlob second = builder.build(files, TIMESTAMP, tempDirectory.resolve("second.tar.gz"));

        assertThat(Files.readAllBytes(second.file())).isEqualTo(Files.readAllBytes(first.file()));
        assertThat(second.diffId()).isEqualTo(first.diffId());
        assertThat(second.descriptor()).isEqualTo(first.descriptor());
    }

    @Test
    void supportsLongPathsAndNonAsciiNames() throws Exception {
        String longName = "references/" + "a".repeat(120) + "/Grüße-" + "b".repeat(100) + ".md";
        Path file = TestSkills.write(skillDirectory, longName, "long");

        LayerBlob blob = builder.build(List.of(SkillFile.file(longName, file, false)), TIMESTAMP,
                tempDirectory.resolve("layer.tar.gz"));

        assertThat(TarEntries.read(blob.file())).extracting(TarEntries.Entry::name).containsExactly(longName);
    }

    @Test
    void failsIfFileCannotBeRead() {
        List<SkillFile> missing = List.of(SkillFile.file("MISSING.md", skillDirectory.resolve("MISSING.md"), false));

        assertThatThrownBy(() -> builder.build(missing, TIMESTAMP, tempDirectory.resolve("layer.tar.gz")))
                .isInstanceOf(de.eitco.cicd.ociskills.core.SkillBuildException.class)
                .hasMessageContaining("MISSING.md");
    }
}
