package de.eitco.cicd.ociskills.core.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillIdTest {

    @Test
    void parsesNamespaceAndName() {
        SkillId id = SkillId.parse("cicd/nexus-test");

        assertThat(id).isEqualTo(new SkillId("cicd", "nexus-test"));
        assertThat(id).hasToString("cicd/nexus-test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nexus-test", "/nexus-test", "cicd/", "a/b/c", "cicd/Nexus", "cicd/../x"})
    void rejectsOtherForms(String value) {
        assertThatThrownBy(() -> SkillId.parse(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(value);
    }
}
