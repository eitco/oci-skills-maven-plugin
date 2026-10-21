package de.eitco.cicd.ociskills.core.oci;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTagStrategyTest {

    @ParameterizedTest
    @CsvSource({"DRAFT, 0.0.1-draft", "TESTING, 0.0.1-testing", "PUBLISHED, 0.0.1"})
    void createsTagsLikeSkillctl(SkillStatus status, String expectedTag) {
        assertThat(new DefaultTagStrategy().tag("0.0.1", status)).isEqualTo(expectedTag);
    }
}
