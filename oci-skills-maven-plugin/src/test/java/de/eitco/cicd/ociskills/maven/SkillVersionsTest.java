package de.eitco.cicd.ociskills.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillVersionsTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "[1.0,2.0); 1.5.3; true",
            "[1.0,2.0); 2.0; false",
            "[1.0,); 10.0; true",
            "1.2.0; 1.2.0; true",
            "1.2.0; 1.2.1; false",
            "1.2.0; 1.2; true",
            "[1.2.0]; 1.2.0; true"})
    void matchesRangesAndExactVersions(String spec, String version, boolean expected) {
        assertThat(SkillVersions.matches(spec, version)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {"1.2.0; [1.2.0]", "[1.0,2.0); [1.0,2.0)", "(,1.0]; (,1.0]"})
    void convertsSpecsToRanges(String spec, String expected) {
        assertThat(SkillVersions.toRange(spec)).isEqualTo(expected);
    }

    @Test
    void rejectsInvalidSpecs() {
        assertThatThrownBy(() -> SkillVersions.matches("[1.0", "1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[1.0");
        assertThatThrownBy(() -> SkillVersions.toRange(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersLikeMaven() {
        assertThat(Stream.of("1.10", "1.2", "1.2-SNAPSHOT", "1.9.1").sorted(SkillVersions.ORDER).toList())
                .isEqualTo(List.of("1.2-SNAPSHOT", "1.2", "1.9.1", "1.10"));
    }
}
