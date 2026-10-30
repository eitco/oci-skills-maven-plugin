package de.eitco.cicd.ociskills.core.archive;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.ZipEntries;
import de.eitco.cicd.ociskills.core.install.TestImages;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillJarWriterTest {

    /**
     * The created annotation of the test images in UTC - DOS times have a precision of two seconds.
     */
    private static final LocalDateTime CREATED = LocalDateTime.parse("2026-10-16T14:45:02");

    @TempDir
    Path tempDirectory;

    private OciImageLayout image(String name) throws Exception {
        return TestImages.build(tempDirectory.resolve("images"), "cicd", name, "1.0.0",
                Map.of("scripts/run.sh", "#!/bin/sh\n", "references/guide.md", "guide"));
    }

    private static Map<String, ZipEntries.Entry> byName(Path jar) throws Exception {
        return ZipEntries.read(jar).stream().collect(Collectors.toMap(ZipEntries.Entry::name, Function.identity()));
    }

    @Test
    void writesTheSkillFilesBelowTheSkillNameAndTheMetadataSortedByPath() throws Exception {
        Path jar = tempDirectory.resolve("nexus-test.jar");

        new SkillJarWriter().write(List.of(image("nexus-test")), jar);

        assertThat(ZipEntries.names(jar)).containsExactly(
                "META-INF/oci-skills/nexus-test/config.json",
                "META-INF/oci-skills/nexus-test/manifest.json",
                "nexus-test/",
                "nexus-test/SKILL.md",
                "nexus-test/references/",
                "nexus-test/references/guide.md",
                "nexus-test/scripts/",
                "nexus-test/scripts/run.sh",
                "nexus-test/skill.yaml");
    }

    @Test
    void keepsTheModesOfTheLayer() throws Exception {
        Path jar = tempDirectory.resolve("nexus-test.jar");

        new SkillJarWriter().write(List.of(image("nexus-test")), jar);

        Map<String, ZipEntries.Entry> entries = byName(jar);
        assertThat(entries.get("nexus-test/").mode()).isEqualTo(0755);
        assertThat(entries.get("nexus-test/scripts/").mode()).isEqualTo(0755);
        assertThat(entries.get("nexus-test/scripts/run.sh").mode()).isEqualTo(0755);
        assertThat(entries.get("nexus-test/SKILL.md").mode()).isEqualTo(0644);
        assertThat(entries.get("nexus-test/references/guide.md").mode()).isEqualTo(0644);
        assertThat(entries.get("META-INF/oci-skills/nexus-test/manifest.json").mode()).isEqualTo(0644);
        assertThat(new String(entries.get("nexus-test/scripts/run.sh").content())).isEqualTo("#!/bin/sh\n");
    }

    @Test
    void storesManifestAndConfigByteForByte() throws Exception {
        OciImageLayout image = image("nexus-test");
        Path jar = tempDirectory.resolve("nexus-test.jar");

        new SkillJarWriter().write(List.of(image), jar);

        Map<String, ZipEntries.Entry> entries = byName(jar);
        assertThat(entries.get("META-INF/oci-skills/nexus-test/manifest.json").content())
                .isEqualTo(Files.readAllBytes(image.blob(image.manifestDescriptor())));
        assertThat(entries.get("META-INF/oci-skills/nexus-test/config.json").content())
                .isEqualTo(Files.readAllBytes(image.blob(image.manifest().config())));
    }

    @Test
    void stampsAllEntriesWithTheCreationTimeOfTheImageWithoutExtraFields() throws Exception {
        Path jar = tempDirectory.resolve("nexus-test.jar");

        new SkillJarWriter().write(List.of(image("nexus-test")), jar);

        assertThat(ZipEntries.read(jar)).allSatisfy(entry -> {
            assertThat(entry.time()).as(entry.name()).isEqualTo(CREATED);
            assertThat(entry.extraFieldCount()).as(entry.name()).isZero();
        });
    }

    @Test
    void isReproducible() throws Exception {
        OciImageLayout image = image("nexus-test");
        Path first = tempDirectory.resolve("first.jar");
        Path second = tempDirectory.resolve("second.jar");

        new SkillJarWriter().write(List.of(image), first);
        new SkillJarWriter().write(List.of(image), second);

        assertThat(second).hasSameBinaryContentAs(first);
    }

    @Test
    void writesSeveralSkillsIntoOneJar() throws Exception {
        Path jar = tempDirectory.resolve("skills.jar");

        new SkillJarWriter().write(List.of(image("beta"), image("alpha")), jar);

        List<String> names = ZipEntries.names(jar);
        assertThat(names).startsWith(
                "META-INF/oci-skills/alpha/config.json",
                "META-INF/oci-skills/alpha/manifest.json",
                "META-INF/oci-skills/beta/config.json",
                "META-INF/oci-skills/beta/manifest.json",
                "alpha/");
        assertThat(names).contains("alpha/SKILL.md", "alpha/scripts/run.sh", "beta/SKILL.md", "beta/scripts/run.sh");
        assertThat(names).isSortedAccordingTo(String::compareTo);
    }

    @Test
    void rejectsTwoSkillsOfTheSameName() throws Exception {
        OciImageLayout first = image("nexus-test");
        OciImageLayout second = TestImages.build(tempDirectory.resolve("other"), "other", "nexus-test", "1.0.0",
                Map.of());

        assertThatThrownBy(() -> new SkillJarWriter().write(List.of(first, second),
                tempDirectory.resolve("skills.jar")))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("nexus-test");
    }
}
