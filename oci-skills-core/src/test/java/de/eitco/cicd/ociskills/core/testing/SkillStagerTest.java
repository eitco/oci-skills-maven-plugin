package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillStagerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void copiesTheFilesOfTheImage() throws Exception {
        Path skill = TestSkills.createDefaultSkill(tempDirectory.resolve("nexus-test"));
        TestSkills.write(skill, "scripts/run.sh", "#!/bin/sh\necho run\n");
        TestSkills.write(skill, "scripts/tool.py", "print('tool')\n");
        TestSkills.write(skill, "notes/private.md", "private");
        Path target = tempDirectory.resolve("staged");
        TestSkills.write(target, "stale.txt", "from a previous run");

        Path staged = new SkillStager().stage(skill,
                new FileSelection(null, List.of("notes/**"), List.of("scripts/*.py")), target);

        assertThat(staged).isEqualTo(target);
        assertThat(Files.readString(staged.resolve("SKILL.md"))).isEqualTo(TestSkills.DEFAULT_SKILL_MD);
        assertThat(staged.resolve("scripts/run.sh")).exists();
        assertThat(Files.isExecutable(staged.resolve("scripts/tool.py"))).isTrue();
        assertThat(Files.isExecutable(staged.resolve("SKILL.md"))).isFalse();
        assertThat(staged.resolve("notes")).doesNotExist();
        assertThat(staged.resolve("stale.txt")).doesNotExist();
    }
}
