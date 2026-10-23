package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.TestSkills;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class TarArchiverTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-16T14:45:03Z");

    @TempDir
    Path tempDirectory;

    @Test
    void archivesTheDirectoryReproducibly() throws Exception {
        Path directory = tempDirectory.resolve("skill-oci");
        TestSkills.write(directory, "nexus-test/index.json", "{}");
        Path script = TestSkills.write(directory, "nexus-test/blobs/sha256/abc", "blob");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        TestSkills.write(directory, "alpha/oci-layout", "layout");

        Path first = tempDirectory.resolve("first.tar");
        Path second = tempDirectory.resolve("second.tar");
        new TarArchiver().archive(directory, TIMESTAMP, first);
        new TarArchiver().archive(directory, TIMESTAMP, second);

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        Map<String, String> entries = new LinkedHashMap<>();
        try (InputStream file = Files.newInputStream(first); TarArchiveInputStream tar = new TarArchiveInputStream(file)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                assertThat(entry.getModTime().toInstant()).isEqualTo(TIMESTAMP);
                entries.put(entry.getName(), Integer.toOctalString(entry.getMode() & 07777)
                        + (entry.isDirectory() ? "" : " " + new String(tar.readAllBytes(), UTF_8)));
            }
        }
        assertThat(entries).containsExactly(
                Map.entry("alpha/", "755"),
                Map.entry("alpha/oci-layout", "644 layout"),
                Map.entry("nexus-test/", "755"),
                Map.entry("nexus-test/blobs/", "755"),
                Map.entry("nexus-test/blobs/sha256/", "755"),
                Map.entry("nexus-test/blobs/sha256/abc", "755 blob"),
                Map.entry("nexus-test/index.json", "644 {}"));
    }
}
