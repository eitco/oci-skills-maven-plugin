package de.eitco.cicd.ociskills.core.skillcard;

import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.TestSkills;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillFrontmatterReaderTest {

    @TempDir
    Path skillDirectory;

    private final SkillFrontmatterReader reader = new SkillFrontmatterReader();

    @Test
    void readsNameAndDescription() throws Exception {
        TestSkills.createDefaultSkill(skillDirectory);

        assertThat(reader.read(skillDirectory).frontmatter())
                .isEqualTo(new SkillFrontmatter("nexus-test", "Test skill for OCI registry"));
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill
                license: MIT
                allowed-tools: Bash Read
                metadata:
                  author: eitco
                ---
                # Nexus Test
                """);

        assertThat(reader.read(skillDirectory).frontmatter())
                .isEqualTo(new SkillFrontmatter("nexus-test", "Test skill", Map.of("author", "eitco")));
    }

    @Test
    void readsStatus() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill
                status: draft
                ---
                # Nexus Test
                """);

        assertThat(reader.read(skillDirectory).frontmatter())
                .isEqualTo(new SkillFrontmatter("nexus-test", "Test skill", Map.of(), "draft"));
    }

    @Test
    void readsManagedFields() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill
                namespace: cicd
                version: 1.10
                ---
                # Nexus Test
                """);

        assertThat(reader.read(skillDirectory).frontmatter())
                .isEqualTo(new SkillFrontmatter("nexus-test", "Test skill", Map.of(), null, "cicd", "1.10"));
    }

    @Test
    void readsMultiLineDescription() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: >
                  Publishes skills.
                  Use it for: registries
                ---
                """);

        assertThat(reader.read(skillDirectory).frontmatter().description()).isEqualTo("Publishes skills. Use it for: registries\n");
    }

    @Test
    void acceptsByteOrderMarkAndWindowsLineEndings() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md",
                "﻿---\r\nname: nexus-test\r\ndescription: Test skill\r\n---\r\n# Nexus Test\r\n");

        SkillMarkdown markdown = reader.read(skillDirectory);
        assertThat(markdown.frontmatter()).isEqualTo(new SkillFrontmatter("nexus-test", "Test skill"));
        assertThat(markdown.body()).isEqualTo("# Nexus Test\r\n".getBytes(UTF_8));
    }

    @Test
    void returnsMissingFieldsAsNull() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                license: MIT
                ---
                """);

        assertThat(reader.read(skillDirectory).frontmatter()).isEqualTo(new SkillFrontmatter(null, null));
    }

    @Test
    void returnsFrontmatterYamlAndUnchangedBody() throws Exception {
        byte[] body = "\n# Nexus Test\n\nUmlaut \u00e4, invalid UTF-8: ".getBytes(UTF_8);
        byte[] invalid = {(byte) 0xff, (byte) 0xfe};
        Files.write(skillDirectory.resolve("SKILL.md"), concat("---\nname: nexus-test\ndescription: d\n---\n"
                .getBytes(UTF_8), body, invalid));

        SkillMarkdown markdown = reader.read(skillDirectory);

        assertThat(markdown.frontmatterYaml()).isEqualTo("name: nexus-test\ndescription: d\n");
        assertThat(markdown.body()).isEqualTo(concat(body, invalid));
    }

    @Test
    void returnsEmptyBodyIfFileEndsWithClosingLine() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", "---\nname: nexus-test\n---");

        assertThat(reader.read(skillDirectory).body()).isEmpty();
    }

    @Test
    void readsMetadataAsStrings() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                metadata:
                  author: eitco
                  revision: 1.10
                ---
                """);

        assertThat(reader.read(skillDirectory).frontmatter().metadata())
                .containsExactly(Map.entry("author", "eitco"), Map.entry("revision", "1.10"));
    }

    @Test
    void failsOnNestedMetadata() {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                metadata:
                  author:
                    name: eitco
                ---
                """);

        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("not a valid frontmatter");
    }

    @Test
    void failsIfSkillMdIsMissing() {
        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining(skillDirectory.resolve("SKILL.md").toString())
                .hasMessageContaining("does not exist");
    }

    @ParameterizedTest
    @ValueSource(strings = {"# Nexus Test\n", "", "\n---\nname: x\n---\n", "name: x\n---\n"})
    void failsWithoutFrontmatter(String content) {
        TestSkills.write(skillDirectory, "SKILL.md", content);

        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("SKILL.md")
                .hasMessageContaining("has no frontmatter");
    }

    @Test
    void failsOnUnterminatedFrontmatter() {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill
                """);

        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("not terminated");
    }

    @Test
    void failsOnEmptyFrontmatter() {
        TestSkills.write(skillDirectory, "SKILL.md", "---\n---\n# Nexus Test\n");

        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void failsWithLineNumberOfSkillMdOnInvalidYaml() {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: [unclosed
                ---
                """);

        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("SKILL.md")
                .hasMessageMatching("(?s).*line [34],.*");
    }

    @Test
    void failsIfFrontmatterIsNoMapping() {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                - nexus-test
                ---
                """);

        assertThatThrownBy(() -> reader.read(skillDirectory))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("not a valid frontmatter");
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            result.writeBytes(part);
        }
        return result.toByteArray();
    }
}
