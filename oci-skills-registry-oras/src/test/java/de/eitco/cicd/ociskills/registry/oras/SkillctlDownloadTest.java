package de.eitco.cicd.ociskills.registry.oras;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillctlDownloadTest {

    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({
            "Linux, amd64, linux_amd64",
            "Linux, x86_64, linux_amd64",
            "Linux, aarch64, linux_arm64",
            "Mac OS X, x86_64, darwin_amd64",
            "Mac OS X, aarch64, darwin_arm64"})
    void mapsSupportedPlatforms(String osName, String osArch, String expected) {
        assertThat(SkillctlDownload.platform(osName, osArch)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"Windows 11, amd64", "Linux, ppc64le", "FreeBSD, amd64"})
    void rejectsUnsupportedPlatforms(String osName, String osArch) {
        assertThat(SkillctlDownload.platform(osName, osArch)).isEmpty();
    }

    @Test
    void extractsTheVerifiedExecutable() throws Exception {
        Path archive = archive("#!/bin/sh\necho skillctl\n");
        Path executable = directory.resolve("tools/skillctl");

        SkillctlDownload.install(archive.toUri(), sha256(archive), executable);

        assertThat(executable).content().isEqualTo("#!/bin/sh\necho skillctl\n");
        assertThat(Files.isExecutable(executable)).isTrue();
        try (var files = Files.list(executable.getParent())) {
            assertThat(files).containsExactly(executable);
        }
    }

    @Test
    void rejectsAnArchiveWithAnotherChecksum() throws Exception {
        Path archive = archive("#!/bin/sh\necho evil\n");
        Path executable = directory.resolve("tools/skillctl");

        assertThatThrownBy(() -> SkillctlDownload.install(archive.toUri(), "0".repeat(64), executable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
        assertThat(executable).doesNotExist();
    }

    @Test
    void reportsFailingDownloadsAsIoException() {
        Path executable = directory.resolve("tools/skillctl");

        assertThatThrownBy(() -> SkillctlDownload.install(directory.resolve("missing.tar.gz").toUri(),
                "0".repeat(64), executable))
                .isInstanceOf(IOException.class);
        assertThat(executable).doesNotExist();
    }

    /**
     * Creates an archive laid out like the release - the executable next to license and readme.
     */
    private Path archive(String executable) throws IOException {
        Path archive = directory.resolve("skillctl.tar.gz");
        try (OutputStream file = Files.newOutputStream(archive);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(file))) {
            entry(tar, "LICENSE", "license");
            entry(tar, "skillctl", executable);
            entry(tar, "README.md", "readme");
        }
        return archive;
    }

    private static void entry(TarArchiveOutputStream tar, String name, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        entry.setMode(0755);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
