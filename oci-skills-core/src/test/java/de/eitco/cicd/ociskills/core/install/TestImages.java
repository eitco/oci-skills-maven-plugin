package de.eitco.cicd.ociskills.core.install;

import de.eitco.cicd.ociskills.core.SkillBuildRequest;
import de.eitco.cicd.ociskills.core.SkillImageBuilder;
import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds skill images to install in tests.
 */
public final class TestImages {

    private TestImages() {
    }

    /**
     * @param files the files of the skill besides {@code SKILL.md} - {@code scripts/**} are executable
     */
    public static OciImageLayout build(Path directory, String namespace, String name, String version,
                                Map<String, String> files) throws Exception {
        Path skill = directory.resolve("src").resolve(name);
        TestSkills.write(skill, "SKILL.md", """
                ---
                name: %s
                description: Test skill %s
                ---

                # Test
                """.formatted(name, name));
        files.forEach((path, content) -> TestSkills.write(skill, path, content));
        Path layout = directory.resolve("oci").resolve(name);
        new SkillImageBuilder().build(new SkillBuildRequest(skill, layout,
                new FileSelection(null, null, List.of("scripts/**")), SkillStatus.PUBLISHED,
                Instant.parse("2026-10-16T14:45:03Z"), new SkillCardSettings(namespace, version),
                directory.resolve("generated").resolve(name)));
        return new OciLayoutReader().read(layout);
    }
}
