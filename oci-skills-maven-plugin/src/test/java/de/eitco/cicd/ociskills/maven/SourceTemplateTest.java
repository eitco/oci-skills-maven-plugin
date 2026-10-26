package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceTemplateTest {

    private static final SkillId NEXUS_TEST = new SkillId("cicd", "nexus-test");

    @Test
    void replacesThePlaceholders() {
        assertThat(new SourceTemplate("groupId", "de.eitco.{namespace}").expand(NEXUS_TEST))
                .isEqualTo("de.eitco.cicd");
        assertThat(new SourceTemplate("artifactId", "{namespace}-{name}-{name}").expand(NEXUS_TEST))
                .isEqualTo("cicd-nexus-test-nexus-test");
        assertThat(new SourceTemplate("repository", "docker/eitco").expand(NEXUS_TEST)).isEqualTo("docker/eitco");
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
                .hasMessageContaining("{namespace}");
    }
}
