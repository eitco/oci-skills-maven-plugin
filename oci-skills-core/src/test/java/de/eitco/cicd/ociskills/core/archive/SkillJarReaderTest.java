package de.eitco.cicd.ociskills.core.archive;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.ZipEntries;
import de.eitco.cicd.ociskills.core.install.TestImages;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillJarReaderTest {

    @TempDir
    Path tempDirectory;

    private OciImageLayout image(String name) throws Exception {
        return TestImages.build(tempDirectory.resolve("images"), "cicd", name, "1.0.0",
                Map.of("scripts/run.sh", "#!/bin/sh\n", "references/guide.md", "guide"));
    }

    private Path jar(OciImageLayout... images) throws Exception {
        Path jar = Files.createTempFile(tempDirectory, "skill-", ".jar");
        new SkillJarWriter().write(List.of(images), jar);
        return jar;
    }

    /**
     * Rewrites the jar with every entry passed through the given function - {@code null} removes the entry.
     */
    private Path rewrite(Path jar, UnaryOperator<ZipEntries.Entry> change, ZipEntries.Entry... added)
            throws IOException {
        List<ZipEntries.Entry> entries = new ArrayList<>();
        for (ZipEntries.Entry entry : ZipEntries.read(jar)) {
            ZipEntries.Entry changed = change.apply(entry);
            if (changed != null) {
                entries.add(changed);
            }
        }
        entries.addAll(List.of(added));
        Path rewritten = Files.createTempFile(tempDirectory, "rewritten-", ".jar");
        ZipEntries.write(rewritten, entries);
        return rewritten;
    }

    private SkillJar read(Path jar) throws SkillBuildException, IOException {
        return new SkillJarReader().read(jar, Files.createTempDirectory(tempDirectory, "work-"));
    }

    /**
     * @return the content of every file by path - suffixed with {@code (x)} if executable
     */
    private static Map<String, String> tree(Path directory) throws IOException {
        Map<String, String> tree = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                tree.put(directory.relativize(path).toString(),
                        Files.readString(path) + (Files.isExecutable(path) ? " (x)" : ""));
            }
        }
        return tree;
    }

    @Test
    void readsTheImageWrittenByTheWriter() throws Exception {
        OciImageLayout image = image("nexus-test");

        SkillJar skill = read(jar(image));

        assertThat(skill.manifestDescriptor()).isEqualTo(image.manifestDescriptor());
        assertThat(skill.manifest()).isEqualTo(image.manifest());
    }

    @Test
    void extractsTheSameFilesAsTheLayer() throws Exception {
        OciImageLayout image = image("nexus-test");
        Path fromLayer = tempDirectory.resolve("from-layer");
        Path fromJar = tempDirectory.resolve("from-jar");
        image.extract(fromLayer);

        read(jar(image)).extract(fromJar);

        assertThat(tree(fromJar))
                .containsEntry("scripts/run.sh", "#!/bin/sh\n (x)")
                .containsEntry("references/guide.md", "guide")
                .containsKeys("SKILL.md", "skill.yaml")
                .isEqualTo(tree(fromLayer));
    }

    @Test
    void rejectsChangedSkillFiles() throws Exception {
        Path jar = rewrite(jar(image("nexus-test")), entry -> entry.name().equals("nexus-test/references/guide.md")
                ? entry.withContent("changed".getBytes(UTF_8)) : entry);

        assertThatThrownBy(() -> read(jar))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("nexus-test")
                .hasMessageContaining("diff_id");
    }

    @Test
    void rejectsLostExecutableBits() throws Exception {
        Path jar = rewrite(jar(image("nexus-test")), entry -> entry.name().equals("nexus-test/scripts/run.sh")
                ? new ZipEntries.Entry(entry.name(), 0644, entry.time(), 0, entry.content()) : entry);

        assertThatThrownBy(() -> read(jar))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("diff_id");
    }

    @Test
    void rejectsAChangedConfig() throws Exception {
        Path jar = rewrite(jar(image("nexus-test")), entry -> entry.name().endsWith("/config.json")
                ? entry.withContent((new String(entry.content(), UTF_8) + " ").getBytes(UTF_8)) : entry);

        assertThatThrownBy(() -> read(jar))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("config");
    }

    @Test
    void rejectsMissingMetadata() throws Exception {
        Path jar = rewrite(jar(image("nexus-test")), entry -> entry.name().endsWith("/manifest.json") ? null : entry);

        assertThatThrownBy(() -> read(jar))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("META-INF/oci-skills/nexus-test/manifest.json");
    }

    @Test
    void rejectsAJarWithoutSkill() throws Exception {
        Path jar = tempDirectory.resolve("empty.jar");
        ZipEntries.write(jar, List.of());

        assertThatThrownBy(() -> read(jar))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("no skill");
    }

    @Test
    void rejectsAJarWithTwoSkills() throws Exception {
        Path jar = jar(image("alpha"), image("beta"));

        assertThatThrownBy(() -> read(jar))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("alpha")
                .hasMessageContaining("beta");
    }

    @Test
    void rejectsEntriesOutsideOfTheSkill() throws Exception {
        Path work = Files.createDirectories(tempDirectory.resolve("deep/work"));
        Path jar = rewrite(jar(image("nexus-test")), UnaryOperator.identity(),
                new ZipEntries.Entry("nexus-test/../../evil.sh", 0755, null, 0, "evil".getBytes(UTF_8)));

        assertThatThrownBy(() -> new SkillJarReader().read(jar, work))
                .isInstanceOf(SkillBuildException.class)
                .hasMessageContaining("nexus-test/../../evil.sh");
        assertThat(tempDirectory.resolve("deep/evil.sh")).doesNotExist();
        assertThat(tempDirectory.resolve("evil.sh")).doesNotExist();
    }
}
