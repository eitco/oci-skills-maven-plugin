package de.eitco.cicd.ociskills.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildTimestampTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:34:56.789Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource({
            "2026-10-16T14:45:03Z,        2026-10-16T14:45:03Z",
            "2026-10-16T16:45:03+02:00,   2026-10-16T14:45:03Z",
            "1792161903,                  2026-10-16T14:45:03Z",
    })
    void usesOutputTimestampForReproducibleBuilds(String outputTimestamp, Instant expected) throws Exception {
        assertThat(BuildTimestamp.resolve(true, outputTimestamp, CLOCK)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void failsForReproducibleBuildsWithoutOutputTimestamp(String outputTimestamp) {
        assertThatThrownBy(() -> BuildTimestamp.resolve(true, outputTimestamp, CLOCK))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("project.build.outputTimestamp")
                .hasMessageContaining("reproducible");
    }

    @Test
    void failsForInvalidOutputTimestamp() {
        assertThatThrownBy(() -> BuildTimestamp.resolve(true, "yesterday", CLOCK))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("yesterday");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-10-16T14:45:03Z"})
    void usesCurrentTimeForNonReproducibleBuilds(String outputTimestamp) throws Exception {
        assertThat(BuildTimestamp.resolve(false, outputTimestamp, CLOCK)).isEqualTo(NOW);
    }
}
