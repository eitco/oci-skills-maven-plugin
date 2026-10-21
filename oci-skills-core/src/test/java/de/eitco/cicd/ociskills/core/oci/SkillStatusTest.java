package de.eitco.cicd.ociskills.core.oci;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillStatusTest {

    @ParameterizedTest
    @CsvSource({"draft, DRAFT", "testing, TESTING", "published, PUBLISHED", " Draft , DRAFT"})
    void parsesSupportedValues(String value, SkillStatus expected) {
        assertThat(SkillStatus.parse(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deprecated", "archived", "latest", ""})
    void rejectsOtherValues(String value) {
        assertThatThrownBy(() -> SkillStatus.parse(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draft, testing, published");
    }
}
