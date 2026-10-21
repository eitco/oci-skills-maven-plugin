package de.eitco.cicd.ociskills.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillDirectoriesTest {

    @TempDir
    Path skills;

    @Test
    void returnsSubdirectoriesSortedByName() throws Exception {
        Files.createDirectories(skills.resolve("beta"));
        Files.createDirectories(skills.resolve("alpha/references"));
        Files.createDirectories(skills.resolve("gamma"));

        assertThat(SkillDirectories.discover(skills))
                .containsExactly(skills.resolve("alpha"), skills.resolve("beta"), skills.resolve("gamma"));
    }

    @Test
    void ignoresFilesAndHiddenDirectories() throws Exception {
        Files.createDirectories(skills.resolve("alpha"));
        Files.createDirectories(skills.resolve(".git"));
        Files.writeString(skills.resolve("README.md"), "not a skill");

        assertThat(SkillDirectories.discover(skills)).containsExactly(skills.resolve("alpha"));
    }

    @Test
    void failsIfDirectoryDoesNotExist() {
        Path missing = skills.resolve("missing");

        assertThatThrownBy(() -> SkillDirectories.discover(missing))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("no skills found")
                .hasMessageContaining(missing.toString());
    }

    @Test
    void failsIfDirectoryContainsNoSkill() throws Exception {
        Files.createDirectories(skills.resolve(".hidden"));
        Files.writeString(skills.resolve("README.md"), "not a skill");

        assertThatThrownBy(() -> SkillDirectories.discover(skills))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("no skills found")
                .hasMessageContaining(skills.toString());
    }
}
