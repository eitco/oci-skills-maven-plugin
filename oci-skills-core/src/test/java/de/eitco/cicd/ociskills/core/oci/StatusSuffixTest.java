package de.eitco.cicd.ociskills.core.oci;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusSuffixTest {

    @ParameterizedTest
    @CsvSource({"DRAFT, nexus-test-draft", "TESTING, nexus-test-testing", "PUBLISHED, nexus-test"})
    void appendsTheStatusUnlessPublished(SkillStatus status, String expected) {
        assertThat(StatusSuffix.append("nexus-test", status)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.0, 1.0.0, PUBLISHED",
            "1.0.0-draft, 1.0.0, DRAFT",
            "1.0.0-testing, 1.0.0, TESTING",
            "1.0.0-SNAPSHOT-draft, 1.0.0-SNAPSHOT, DRAFT",
            "1.0.0-SNAPSHOT, 1.0.0-SNAPSHOT, PUBLISHED",
            "draft, draft, PUBLISHED"})
    void splitsTheStatusOff(String value, String base, SkillStatus status) {
        assertThat(StatusSuffix.split(value)).isEqualTo(new StatusSuffix.Split(base, status));
    }
}
