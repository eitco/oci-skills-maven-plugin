package de.eitco.cicd.ociskills.core.install;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillInstallerTest {

    @TempDir
    Path tempDirectory;

    private Path skills() {
        return tempDirectory.resolve(".claude/skills");
    }

    private OciImageLayout image(String name, String version, Map<String, String> files) throws Exception {
        return TestImages.build(tempDirectory.resolve("images").resolve(version), "cicd", name, version, files);
    }

    private static SkillOrigin origin(OciImageLayout image, String name, String version) {
        return new SkillOrigin(new SkillId("cicd", name), version, SkillStatus.PUBLISHED,
                image.manifestDescriptor().digest(), "maven");
    }

    @Test
    void installsTheLayerAndMarksTheSkill() throws Exception {
        OciImageLayout image = image("nexus-test", "1.0.0", Map.of("scripts/run.sh", "#!/bin/sh\n"));
        SkillInstaller installer = new SkillInstaller(skills());

        installer.install(image, origin(image, "nexus-test", "1.0.0"));

        Path skill = skills().resolve("nexus-test");
        assertThat(skill.resolve("SKILL.md")).content().contains("name: nexus-test", "version: 1.0.0");
        assertThat(skill.resolve("skill.yaml")).exists();
        assertThat(Files.isExecutable(skill.resolve("scripts/run.sh"))).isTrue();
        assertThat(skill.resolve(SkillInstaller.ORIGIN_FILE)).exists();
        assertThat(installer.installed()).containsExactly(
                Map.entry("nexus-test", origin(image, "nexus-test", "1.0.0")));
    }

    @Test
    void replacesAPreviouslyInstalledVersion() throws Exception {
        SkillInstaller installer = new SkillInstaller(skills());
        OciImageLayout first = image("nexus-test", "1.0.0", Map.of("references/old.md", "old"));
        installer.install(first, origin(first, "nexus-test", "1.0.0"));
        OciImageLayout second = image("nexus-test", "1.1.0", Map.of("references/new.md", "new"));

        installer.install(second, origin(second, "nexus-test", "1.1.0"));

        Path skill = skills().resolve("nexus-test");
        assertThat(skill.resolve("references/new.md")).hasContent("new");
        assertThat(skill.resolve("references/old.md")).doesNotExist();
        assertThat(installer.installed().get("nexus-test").version()).isEqualTo("1.1.0");
        assertThat(skills()).isDirectoryNotContaining(path -> !path.getFileName().toString().equals("nexus-test"));
    }

    @Test
    void neverTouchesSkillsWrittenByHand() throws Exception {
        TestSkills.write(skills(), "nexus-test/SKILL.md", "by hand");
        OciImageLayout image = image("nexus-test", "1.0.0", Map.of());
        SkillInstaller installer = new SkillInstaller(skills());

        assertThatThrownBy(() -> installer.install(image, origin(image, "nexus-test", "1.0.0")))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("nexus-test")
                .hasMessageContaining("not installed by the plugin");
        assertThat(skills().resolve("nexus-test/SKILL.md")).hasContent("by hand");
        assertThat(installer.installed()).isEmpty();
        assertThatThrownBy(() -> installer.remove("nexus-test")).isInstanceOf(SkillPullException.class);
        assertThat(skills().resolve("nexus-test/SKILL.md")).hasContent("by hand");
    }

    @Test
    void rejectsAnImageOfAnotherSkill() throws Exception {
        OciImageLayout image = image("nexus-test", "1.0.0", Map.of());

        assertThatThrownBy(() -> new SkillInstaller(skills()).install(image, origin(image, "other", "1.0.0")))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("cicd/other");
        assertThat(skills().resolve("other")).doesNotExist();
    }

    @Test
    void rejectsAnImageOfAnotherNamespace() throws Exception {
        OciImageLayout image = image("nexus-test", "1.0.0", Map.of());
        SkillOrigin origin = new SkillOrigin(new SkillId("other", "nexus-test"), "1.0.0", SkillStatus.PUBLISHED,
                image.manifestDescriptor().digest(), "maven");

        assertThatThrownBy(() -> new SkillInstaller(skills()).install(image, origin))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("other/nexus-test");
    }

    @Test
    void removesInstalledSkills() throws Exception {
        OciImageLayout image = image("nexus-test", "1.0.0", Map.of());
        SkillInstaller installer = new SkillInstaller(skills());
        installer.install(image, origin(image, "nexus-test", "1.0.0"));

        installer.remove("nexus-test");

        assertThat(skills().resolve("nexus-test")).doesNotExist();
        assertThat(installer.installed()).isEmpty();
    }

    @Test
    void listsInstalledSkillsInAManagedBlockOfGitignore() throws Exception {
        TestSkills.write(skills(), ".gitignore", "local-only/\n");
        TestSkills.write(skills(), "by-hand/SKILL.md", "by hand");
        SkillInstaller installer = new SkillInstaller(skills());
        for (String name : new String[]{"beta", "alpha"}) {
            OciImageLayout image = image(name, "1.0.0", Map.of());
            installer.install(image, origin(image, name, "1.0.0"));
        }

        installer.writeGitignore();

        assertThat(skills().resolve(".gitignore")).hasContent("""
                local-only/
                # BEGIN managed by oci-skills-maven-plugin
                /alpha/
                /beta/
                # END managed by oci-skills-maven-plugin
                """);

        installer.remove("beta");
        installer.writeGitignore();

        assertThat(skills().resolve(".gitignore")).hasContent("""
                local-only/
                # BEGIN managed by oci-skills-maven-plugin
                /alpha/
                # END managed by oci-skills-maven-plugin
                """);
    }

    @Test
    void readsNothingFromAMissingDirectory() throws Exception {
        assertThat(new SkillInstaller(skills()).installed()).isEmpty();
    }
}
