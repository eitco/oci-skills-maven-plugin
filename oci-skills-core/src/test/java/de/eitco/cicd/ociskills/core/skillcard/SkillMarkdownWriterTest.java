package de.eitco.cicd.ociskills.core.skillcard;

import de.eitco.cicd.ociskills.core.TestSkills;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SkillMarkdownWriterTest {

    @TempDir
    Path skillDirectory;

    private final SkillFrontmatterReader reader = new SkillFrontmatterReader();
    private final SkillMarkdownWriter writer = new SkillMarkdownWriter();

    private String write(String skillMd, String version) throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", skillMd);
        SkillMarkdown markdown = reader.read(skillDirectory);
        SkillCard skillCard = SkillCard.of(markdown.frontmatter(), new SkillCardSettings("cicd", version));
        return new String(writer.write(markdown, skillCard), UTF_8);
    }

    @Test
    void addsManagedValuesAsMetadata() throws Exception {
        assertThat(write(TestSkills.DEFAULT_SKILL_MD, "0.0.1")).isEqualTo("""
                ---
                name: nexus-test
                description: Test skill for OCI registry
                metadata:
                  namespace: cicd
                  version: 0.0.1
                ---

                # Nexus Test

                This skill verifies publishing to an OCI registry.
                """);
    }

    @Test
    void keepsOrderUnknownFieldsAndExistingMetadata() throws Exception {
        assertThat(write("""
                ---
                description: Test skill
                name: nexus-test
                metadata:
                  author: eitco
                license: MIT
                allowed-tools: Bash Read
                ---
                # Nexus Test
                """, "1.0.0-SNAPSHOT")).isEqualTo("""
                ---
                description: Test skill
                name: nexus-test
                metadata:
                  author: eitco
                  namespace: cicd
                  version: 1.0.0-SNAPSHOT
                license: MIT
                allowed-tools: Bash Read
                ---
                # Nexus Test
                """);
    }

    @Test
    void keepsBodyUnchanged() throws Exception {
        String body = "\r\n  # Nexus Test  \r\n\r\n---\r\nnot: frontmatter\r\n";

        assertThat(write("---\nname: nexus-test\ndescription: d\n---\n" + body, "0.0.1")).endsWith("---\n" + body);
    }

    @Test
    void writesVersionThatLooksLikeANumberAsString() throws Exception {
        String content = write(TestSkills.DEFAULT_SKILL_MD, "1.10");

        assertThat(content).contains("  version: \"1.10\"\n");
        TestSkills.write(skillDirectory, "SKILL.md", content);
        assertThat(reader.read(skillDirectory).frontmatter().metadata()).containsEntry("version", "1.10");
    }

    @Test
    void keepsNumbersOfOtherFieldsUnchanged() throws Exception {
        assertThat(write("""
                ---
                name: nexus-test
                description: d
                metadata:
                  revision: 1.10
                ---
                """, "0.0.1")).contains("  revision: 1.10\n");
    }

    @Test
    void writesFrontmatterThatCanBeReadBack() throws Exception {
        String content = write("""
                ---
                name: nexus-test
                description: "Use it for: registries # really"
                ---
                """, "0.0.1");

        TestSkills.write(skillDirectory, "SKILL.md", content);
        assertThat(reader.read(skillDirectory).frontmatter().description()).isEqualTo("Use it for: registries # really");
    }
}
