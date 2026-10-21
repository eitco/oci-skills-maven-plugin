package de.eitco.cicd.ociskills.core;

import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper to create skill directories in tests.
 */
public final class TestSkills {

    public static final SkillCardSettings DEFAULT_SETTINGS = new SkillCardSettings("cicd", "0.0.1");

    /**
     * The skill.yaml generated for the default skill with the default settings.
     */
    public static final String DEFAULT_SKILL_YAML = """
            apiVersion: skillimage.io/v1alpha1
            kind: SkillCard
            metadata:
              name: nexus-test
              namespace: cicd
              version: 0.0.1
              description: Test skill for OCI registry
            spec:
              prompt: SKILL.md
            """;

    public static final String DEFAULT_SKILL_MD = """
            ---
            name: nexus-test
            description: Test skill for OCI registry
            ---

            # Nexus Test

            This skill verifies publishing to an OCI registry.
            """;

    private TestSkills() {
    }

    public static Path createDefaultSkill(Path directory) {
        write(directory, "SKILL.md", DEFAULT_SKILL_MD);
        return directory;
    }

    public static Path write(Path directory, String relativePath, String content) {
        try {
            Path file = directory.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
