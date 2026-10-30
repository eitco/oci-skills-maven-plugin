package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarExtractorTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-16T14:45:03Z");

    @TempDir
    Path tempDirectory;

    @Test
    void extractsWhatTheLayerBuilderWrites() throws Exception {
        Path source = tempDirectory.resolve("source");
        TestSkills.write(source, "SKILL.md", "skill");
        Path script = TestSkills.write(source, "scripts/run.sh", "#!/bin/sh");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        String longPath = "references/" + "very-long-directory-name/".repeat(8) + "guide-mit-ümlaut.md";
        TestSkills.write(source, longPath, "guide");
        Path layer = tempDirectory.resolve("layer.tar.gz");
        new TarLayerBuilder().build(new SkillFileCollector(new FileSelection(null, List.of(), null)).collect(source),
                TIMESTAMP, layer);

        Path target = tempDirectory.resolve("target");
        try (InputStream in = new GZIPInputStream(Files.newInputStream(layer))) {
            new TarExtractor().extract(in, target);
        }

        assertThat(target.resolve("SKILL.md")).hasContent("skill");
        assertThat(target.resolve("scripts/run.sh")).hasContent("#!/bin/sh");
        assertThat(Files.isExecutable(target.resolve("scripts/run.sh"))).isTrue();
        assertThat(Files.isExecutable(target.resolve("SKILL.md"))).isFalse();
        assertThat(target.resolve(longPath)).hasContent("guide");
    }

    @Test
    void rejectsEntriesOutsideOfTheTarget() throws Exception {
        byte[] tar = tar("../evil.sh", TarArchiveEntry.LF_NORMAL);

        assertThatThrownBy(() -> new TarExtractor().extract(new ByteArrayInputStream(tar),
                tempDirectory.resolve("target")))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("../evil.sh");
        assertThat(tempDirectory.resolve("evil.sh")).doesNotExist();
    }

    @Test
    void rejectsAbsoluteEntries() throws Exception {
        byte[] tar = tar("/tmp/evil.sh", TarArchiveEntry.LF_NORMAL);

        assertThatThrownBy(() -> new TarExtractor().extract(new ByteArrayInputStream(tar),
                tempDirectory.resolve("target")))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("/tmp/evil.sh");
    }

    @Test
    void rejectsLinks() throws Exception {
        byte[] tar = tar("link", TarArchiveEntry.LF_SYMLINK);

        assertThatThrownBy(() -> new TarExtractor().extract(new ByteArrayInputStream(tar),
                tempDirectory.resolve("target")))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("link");
    }

    private static byte[] tar(String name, byte type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            TarArchiveEntry entry = new TarArchiveEntry(name, type, true);
            byte[] content = "evil".getBytes(UTF_8);
            if (type == TarArchiveEntry.LF_SYMLINK) {
                entry.setLinkName("/etc/passwd");
            } else {
                entry.setSize(content.length);
            }
            tar.putArchiveEntry(entry);
            if (type != TarArchiveEntry.LF_SYMLINK) {
                tar.write(content);
            }
            tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }
}
