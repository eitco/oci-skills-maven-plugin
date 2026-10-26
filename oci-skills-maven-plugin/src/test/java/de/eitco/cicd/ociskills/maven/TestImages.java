package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildRequest;
import de.eitco.cicd.ociskills.core.SkillImageBuilder;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Builds skill images in tests.
 */
final class TestImages {

    private TestImages() {
    }

    /**
     * @param content the body of {@code SKILL.md} - to build images of the same version with different digests
     */
    static OciImageLayout build(Path directory, String namespace, String name, String version, SkillStatus status,
                                String content) throws Exception {
        Path root = Files.createTempDirectory(Files.createDirectories(directory), name + "-" + version + "-");
        Path skill = Files.createDirectories(root.resolve("src").resolve(name));
        Files.writeString(skill.resolve("SKILL.md"), """
                ---
                name: %s
                description: Test skill %s
                ---

                %s
                """.formatted(name, name, content));
        Path layout = root.resolve("oci");
        new SkillImageBuilder().build(new SkillBuildRequest(skill, layout, FileSelection.defaults(), status,
                Instant.parse("2026-10-16T14:45:03Z"), new SkillCardSettings(namespace, version),
                root.resolve("generated")));
        return new OciLayoutReader().read(layout);
    }
}
