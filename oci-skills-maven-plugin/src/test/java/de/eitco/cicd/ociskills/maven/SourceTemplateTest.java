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
                .hasMessageContaining("{repository.parent}");
    }
}
