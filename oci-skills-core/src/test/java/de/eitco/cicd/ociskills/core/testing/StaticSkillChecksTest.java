package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.report.TestCase;
import de.eitco.cicd.ociskills.core.report.TestStatus;
import de.eitco.cicd.ociskills.core.report.TestSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaticSkillChecksTest {

    @TempDir
    Path tempDirectory;

    private Path skill;

    private final StaticSkillChecks checks = new StaticSkillChecks();

    @BeforeEach
    void createSkill() {
        skill = tempDirectory.resolve("nexus-test");
    }

    private void skillMd(String body) {
        TestSkills.write(skill, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill for OCI registry
                ---
                """ + body);
    }

    private Path script(String path, String content, boolean executable) throws Exception {
        Path file = TestSkills.write(skill, path, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(executable ? "rwxr-xr-x" : "rw-r--r--"));
        return file;
    }

    private List<Finding> check() throws Exception {
        return check(FileSelection.defaults());
    }

    private List<Finding> check(FileSelection selection) throws Exception {
        return checks.check(skill, selection, TestSkills.DEFAULT_SETTINGS);
    }

    @Test
    void acceptsAValidSkill() throws Exception {
        skillMd("""
                See [the guide](references/guide.md#usage), [nexus](https://nexus.eitco.de), [top](#top) and
                ![diagram](assets/diagram.png "Diagram"). Run `scripts/run.sh`.

                [ref]: references/guide.md
                """);
        TestSkills.write(skill, "references/guide.md", "Back to [the skill](../SKILL.md).");
        TestSkills.write(skill, "assets/diagram.png", "png");
        script("scripts/run.sh", "#!/bin/sh\necho run\n", true);

        assertThat(check()).isEmpty();
    }

    @Test
    void reportsFrontmatterProblems() throws Exception {
        TestSkills.write(skill, "SKILL.md", "---\nname: other\n---\n");

        assertThat(check()).extracting(Finding::check, Finding::severity)
                .contains(org.assertj.core.groups.Tuple.tuple("frontmatter", Finding.Severity.ERROR));
    }

    @Test
    void reportsUnsupportedStatus() throws Exception {
        TestSkills.write(skill, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill for OCI registry
                status: released
                ---
                """);

        assertThat(check()).containsExactly(Finding.error("frontmatter",
                "SKILL.md frontmatter: status 'released' is not supported, supported: draft, testing, published"));
    }

    @Test
    void reportsMissingSkillMd() throws Exception {
        Files.createDirectories(skill);

        assertThat(check()).extracting(Finding::check).containsExactly("frontmatter");
    }

    @Test
    void reportsBrokenLinks() throws Exception {
        skillMd("See [the guide](references/missing.md) and ![img](<assets/my image.png>).\n");

        assertThat(check()).containsExactly(
                Finding.error("links", "SKILL.md: link to references/missing.md - the file does not exist"),
                Finding.error("links", "SKILL.md: link to assets/my image.png - the file does not exist"));
    }

    @Test
    void reportsLinksPointingOutsideOfTheSkill() throws Exception {
        skillMd("See [the parent](../README.md) and [root](/etc/passwd).\n");
        TestSkills.write(tempDirectory, "README.md", "outside");

        assertThat(check()).extracting(Finding::message).containsExactly(
                "SKILL.md: link to ../README.md - points outside of the skill directory",
                "SKILL.md: link to /etc/passwd - points outside of the skill directory");
    }

    @Test
    void reportsLinksToFilesThatAreNotInTheImage() throws Exception {
        skillMd("See [notes](notes/todo.md).\n");
        TestSkills.write(skill, "notes/todo.md", "todo");

        assertThat(check(new FileSelection(null, List.of("notes/**"), null))).containsExactly(
                Finding.error("links", "SKILL.md: link to notes/todo.md - the file is excluded from the image"));
    }

    @Test
    void ignoresLinksInCode() throws Exception {
        skillMd("""
                Use `[x](missing-inline.md)` literally.

                ```markdown
                [y](missing-fenced.md)
                ```
                """);

        assertThat(check()).isEmpty();
    }

    @Test
    void reportsLinksInOtherMarkdownFilesRelativeToTheirDirectory() throws Exception {
        skillMd("See [the guide](references/guide.md).\n");
        TestSkills.write(skill, "references/guide.md", "See [details](details.md).");

        assertThat(check()).containsExactly(Finding.error("links",
                "references/guide.md: link to details.md - the file does not exist"));
    }

    @Test
    void warnsAboutScriptsThatAreNotExecutable() throws Exception {
        skillMd("Run `python scripts/tool.py`.\n");
        script("scripts/tool.py", "print('tool')\n", false);

        assertThat(check()).containsExactly(Finding.warning("scripts",
                "scripts/tool.py is not executable - set the executable bit or add it to <executables>"));
    }

    @Test
    void acceptsScriptsMadeExecutableByPattern() throws Exception {
        skillMd("Run `scripts/tool.py`.\n");
        script("scripts/tool.py", "#!/usr/bin/env python3\nprint('tool')\n", false);

        assertThat(check(new FileSelection(null, null, List.of("scripts/*.py")))).isEmpty();
    }

    @Test
    void reportsExecutableTextScriptsWithoutShebang() throws Exception {
        skillMd("Run `scripts/run.sh`.\n");
        script("scripts/run.sh", "echo run\n", true);

        assertThat(check()).containsExactly(Finding.error("scripts",
                "scripts/run.sh is executable but does not start with a shebang line ('#!')"));
    }

    @Test
    void acceptsExecutableBinariesWithoutShebang() throws Exception {
        skillMd("Run `scripts/tool`.\n");
        Path binary = skill.resolve("scripts/tool");
        Files.createDirectories(binary.getParent());
        Files.write(binary, new byte[]{0x7f, 'E', 'L', 'F', 0, 1, 2});
        Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwxr-xr-x"));

        assertThat(check()).isEmpty();
    }

    @Test
    void warnsAboutFilesNoMarkdownRefersTo() throws Exception {
        skillMd("Nothing here.\n");
        TestSkills.write(skill, "references/unused.md", "unused");
        script("scripts/run.sh", "#!/bin/sh\n", true);

        assertThat(check()).containsExactly(
                Finding.warning("orphans", "references/unused.md is not referenced by any markdown file"),
                Finding.warning("orphans", "scripts/run.sh is not referenced by any markdown file"));
    }

    @Test
    void warnsAboutLongPrompts() throws Exception {
        skillMd("line\n".repeat(StaticSkillChecks.MAX_PROMPT_LINES));

        assertThat(check()).containsExactly(Finding.warning("size",
                "SKILL.md has 504 lines - keep it below 500 lines and move details into referenced files"));
    }

    @Test
    void createsOneTestCasePerCheck() {
        List<Finding> findings = List.of(
                Finding.error("links", "SKILL.md: link to a.md - the file does not exist"),
                Finding.error("links", "SKILL.md: link to b.md - the file does not exist"),
                Finding.warning("orphans", "c.md is not referenced by any markdown file"));

        TestSuite suite = StaticSkillChecks.toSuite("skill.nexus-test.static", findings, false);

        assertThat(suite.name()).isEqualTo("skill.nexus-test.static");
        assertThat(suite.cases()).extracting(TestCase::name).containsExactlyElementsOf(StaticSkillChecks.CHECKS);
        TestCase links = suite.cases().get(1);
        assertThat(links.status()).isEqualTo(TestStatus.FAILED);
        assertThat(links.message()).isEqualTo("SKILL.md: link to a.md - the file does not exist\n"
                + "SKILL.md: link to b.md - the file does not exist");
        TestCase orphans = suite.cases().get(3);
        assertThat(orphans.status()).isEqualTo(TestStatus.PASSED);
        assertThat(orphans.output()).isEqualTo("warning: c.md is not referenced by any markdown file");
        assertThat(suite.status()).isEqualTo(TestStatus.FAILED);
    }

    @Test
    void warningsFailWithFailOnWarning() {
        TestSuite suite = StaticSkillChecks.toSuite("suite",
                List.of(Finding.warning("size", "SKILL.md has 600 lines")), true);

        assertThat(suite.cases().get(4).status()).isEqualTo(TestStatus.FAILED);
        assertThat(suite.cases().get(4).message()).isEqualTo("SKILL.md has 600 lines");
    }
}
