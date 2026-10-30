package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceTemplateTest {

    private static final SkillId NEXUS_TEST = new SkillId("cicd", "nexus-test");
    private static final SkillId NEXUS_ADMIN_TEST = new SkillId("cicd", "nexus-admin-test");

    @Test
    void replacesThePlaceholders() {
        assertThat(new SourceTemplate("groupId", "de.eitco.{namespace}").expand(NEXUS_TEST, null))
                .isEqualTo("de.eitco.cicd");
        assertThat(new SourceTemplate("artifactId", "{namespace}-{name}-{name}").expand(NEXUS_TEST, null))
                .isEqualTo("cicd-nexus-test-nexus-test");
        assertThat(new SourceTemplate("repository", "docker/eitco").expand(NEXUS_TEST, null))
                .isEqualTo("docker/eitco");
    }

    @ParameterizedTest
    @CsvSource({
            "{repository},                    teams/acme/skills,  teams.acme.skills",
            "{repository.parent},             teams/acme/skills,  teams.acme",
            "{repository.last},               teams/acme/skills,  skills",
            "com.example.{repository.parent}, teams/acme/skills,  com.example.teams.acme",
            "{repository.last}-{namespace},   teams/acme/skills,  skills-cicd",
    })
    void replacesTheRepositoryPlaceholders(String template, String repository, String expected) {
        assertThat(new SourceTemplate("groupId", template).expand(NEXUS_TEST, repository)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "{name[0]},       nexus",
            "{name[1]},       admin",
            "{name[-1]},      test",
            "{name[-3]},      nexus",
            "{name[1:]},      admin-test",
            "{name[:-1]},     nexus-admin",
            "{name[0:2]},     nexus-admin",
            "{name[-2:]},     admin-test",
            "{name[:]},       nexus-admin-test",
            "{name[1:10]},    admin-test",
            "{namespace[0]},  cicd",
            "{namespace[1:]}x, x",
            "x-{name[2:1]},   x",
    })
    void replacesIndexedNameParts(String template, String expected) {
        assertThat(new SourceTemplate("artifactId", template).expand(NEXUS_ADMIN_TEST, null)).isEqualTo(expected);
    }

    @Test
    void expandsAnEmptySliceToAnEmptyValue() {
        assertThat(new SourceTemplate("classifier", "{name[:0]}").expand(NEXUS_ADMIN_TEST, null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "{repository[0]},    teams/acme/skills, teams",
            "{repository[-1]},   teams/acme/skills, skills",
            "{repository[1:]},   teams/acme/skills, acme.skills",
            "{repository[:-1]},  teams/acme/skills, teams.acme",
            "{repository[5:]}.x, teams/acme/skills, x",
    })
    void replacesIndexedRepositoryParts(String template, String repository, String expected) {
        assertThat(new SourceTemplate("groupId", template).expand(NEXUS_ADMIN_TEST, repository)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{name[3]}", "{name[-4]}"})
    void rejectsIndicesOutOfRange(String template) {
        assertThatThrownBy(() -> new SourceTemplate("artifactId", template).expand(NEXUS_ADMIN_TEST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cicd/nexus-admin-test")
                .hasMessageContaining(template);
    }

    @Test
    void dropsDashesAndDotsAtTheEnds() {
        SkillId solo = new SkillId("cicd", "solo");

        assertThat(new SourceTemplate("artifactId", "skills-{name[1:]}").expand(solo, null)).isEqualTo("skills");
        assertThat(new SourceTemplate("artifactId", "{name[1:]}-skills").expand(solo, null)).isEqualTo("skills");
        assertThat(new SourceTemplate("groupId", "com..example.{name[1:]}").expand(solo, null))
                .isEqualTo("com.example");
    }

    @Test
    void dropsSuperfluousDots() {
        SourceTemplate groupId = new SourceTemplate("groupId", "com.example.{repository.parent}");

        assertThat(groupId.expand(NEXUS_TEST, "skills")).isEqualTo("com.example");
        assertThat(new SourceTemplate("groupId", "{repository.parent}.{repository.last}")
                .expand(NEXUS_TEST, "skills")).isEqualTo("skills");
    }

    @Test
    void needsARepositoryForTheRepositoryPlaceholders() {
        SourceTemplate groupId = new SourceTemplate("groupId", "com.example.{repository.parent}");

        assertThatThrownBy(() -> groupId.expand(NEXUS_TEST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("<repository>")
                .hasMessageContaining("com.example.{repository.parent}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsEmptyTemplates(String template) {
        assertThatThrownBy(() -> new SourceTemplate("groupId", template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupId");
    }

    @Test
    void rejectsUnknownPlaceholders() {
        assertThatThrownBy(() -> new SourceTemplate("groupId", "de.eitco.{group}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{group}")
                .hasMessageContaining("{namespace}")
                .hasMessageContaining("{repository.parent}")
                .hasMessageContaining("{name[i]}")
                .hasMessageContaining("{name[from:to]}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{name[]}", "{name[a]}", "{name.first}", "{name[1:2:3]}", "{repository[0}"})
    void rejectsInvalidPlaceholders(String template) {
        assertThatThrownBy(() -> new SourceTemplate("artifactId", template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("artifactId")
                .hasMessageContaining(template);
    }
}
