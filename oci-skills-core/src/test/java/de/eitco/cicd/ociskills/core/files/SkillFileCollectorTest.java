package de.eitco.cicd.ociskills.core.files;

import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.TestSkills;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillFileCollectorTest {

    @TempDir
    Path tempDirectory;

    private Path skillDirectory;

    @BeforeEach
    void createSkill() throws Exception {
        skillDirectory = Files.createDirectory(tempDirectory.resolve("skill"));
        TestSkills.createDefaultSkill(skillDirectory);
        TestSkills.write(skillDirectory, "references/guide.md", "guide");
        TestSkills.write(skillDirectory, "references/deep/more.md", "more");
        TestSkills.write(skillDirectory, "scripts/run.sh", "#!/bin/sh");
        TestSkills.write(skillDirectory, "assets/logo.txt", "logo");
    }

    private static List<String> paths(List<SkillFile> files) {
        return files.stream().map(SkillFile::path).toList();
    }

    private List<SkillFile> collect(FileSelection selection) throws Exception {
        return new SkillFileCollector(selection).collect(skillDirectory);
    }

    @Test
    void includesAllRegularFilesAndTheirDirectoriesSortedByPath() throws Exception {
        assertThat(paths(collect(FileSelection.defaults()))).containsExactly(
                "SKILL.md",
                "assets/",
                "assets/logo.txt",
                "references/",
                "references/deep/",
                "references/deep/more.md",
                "references/guide.md",
                "scripts/",
                "scripts/run.sh");
    }

    @Test
    void marksDirectories() throws Exception {
        List<SkillFile> files = collect(FileSelection.defaults());

        assertThat(files).filteredOn(SkillFile::directory).extracting(SkillFile::path)
                .containsExactly("assets/", "references/", "references/deep/", "scripts/");
    }

    @Test
    void excludesGitAndTargetByDefault() throws Exception {
        TestSkills.write(skillDirectory, ".git/config", "x");
        TestSkills.write(skillDirectory, "target/out.txt", "x");

        assertThat(paths(collect(FileSelection.defaults())))
                .noneMatch(path -> path.startsWith(".git") || path.startsWith("target"));
    }

    @Test
    void appliesConfiguredExcludes() throws Exception {
        List<SkillFile> files = collect(new FileSelection(null, List.of("assets/**"), null));

        assertThat(paths(files)).noneMatch(path -> path.startsWith("assets"));
    }

    @Test
    void appliesConfiguredIncludesAndOnlyAddsNeededDirectories() throws Exception {
        List<SkillFile> files = collect(new FileSelection(List.of("**/*.md"), null, null));

        assertThat(paths(files)).containsExactly(
                "SKILL.md", "references/", "references/deep/", "references/deep/more.md", "references/guide.md");
    }

    @Test
    void excludesWinOverIncludes() throws Exception {
        List<SkillFile> files = collect(new FileSelection(List.of("**/*.md"), List.of("references/**"), null));

        assertThat(paths(files)).containsExactly("SKILL.md");
    }

    @Test
    void marksFilesMatchingExecutablePatternsAsExecutable() throws Exception {
        List<SkillFile> files = collect(new FileSelection(null, null, List.of("scripts/**")));

        assertThat(files).filteredOn(SkillFile::executable).extracting(SkillFile::path)
                .containsExactly("scripts/run.sh");
    }

    @Test
    void marksFilesWithExecutableBitAsExecutable() throws Exception {
        Files.setPosixFilePermissions(skillDirectory.resolve("assets/logo.txt"),
                PosixFilePermissions.fromString("rwxr-xr-x"));

        List<SkillFile> files = collect(FileSelection.defaults());

        assertThat(files).filteredOn(SkillFile::executable).extracting(SkillFile::path)
                .containsExactly("assets/logo.txt");
    }

    @Test
    void resolvesFileSymlinksPointingInside() throws Exception {
        Files.createSymbolicLink(skillDirectory.resolve("references/link.md"),
                skillDirectory.resolve("references/guide.md"));

        List<SkillFile> files = collect(FileSelection.defaults());

        assertThat(files).filteredOn(file -> file.path().equals("references/link.md")).singleElement()
                .satisfies(file -> {
                    assertThat(file.directory()).isFalse();
                    assertThat(Files.readString(file.source())).isEqualTo("guide");
                });
    }

    @Test
    void resolvesDirectorySymlinksPointingInside() throws Exception {
        Files.createSymbolicLink(skillDirectory.resolve("docs"), skillDirectory.resolve("references/deep"));

        assertThat(paths(collect(FileSelection.defaults()))).contains("docs/", "docs/more.md");
    }

    @Test
    void rejectsFileSymlinksPointingOutside() throws Exception {
        Path outside = TestSkills.write(tempDirectory, "outside.md", "outside");
        Files.createSymbolicLink(skillDirectory.resolve("references/link.md"), outside);

        assertThatThrownBy(() -> collect(FileSelection.defaults()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("references/link.md")
                .hasMessageContaining("outside of the skill directory");
    }

    @Test
    void rejectsDirectorySymlinksPointingOutside() throws Exception {
        Files.createDirectories(tempDirectory.resolve("outside"));
        Files.createSymbolicLink(skillDirectory.resolve("docs"), tempDirectory.resolve("outside"));

        assertThatThrownBy(() -> collect(FileSelection.defaults()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("docs")
                .hasMessageContaining("outside of the skill directory");
    }

    @Test
    void ignoresExcludedSymlinksPointingOutside() throws Exception {
        Path outside = TestSkills.write(tempDirectory, "outside.md", "outside");
        Files.createDirectories(skillDirectory.resolve(".git"));
        Files.createSymbolicLink(skillDirectory.resolve(".git/link"), outside);

        assertThat(paths(collect(FileSelection.defaults()))).doesNotContain(".git/link");
    }

    @Test
    void rejectsSymlinkCycles() throws Exception {
        Files.createSymbolicLink(skillDirectory.resolve("references/loop"), skillDirectory.resolve("references"));

        assertThatThrownBy(() -> collect(FileSelection.defaults()))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("references/loop");
    }

    @Test
    void sortsByUtf8Bytes() throws Exception {
        TestSkills.write(skillDirectory, "Z.md", "z");
        TestSkills.write(skillDirectory, "a.md", "a");
        TestSkills.write(skillDirectory, "ä.md", "ae");

        List<String> paths = paths(collect(new FileSelection(List.of("*.md"), null, null)));

        assertThat(paths).containsExactly("SKILL.md", "Z.md", "a.md", "ä.md");
    }
}
