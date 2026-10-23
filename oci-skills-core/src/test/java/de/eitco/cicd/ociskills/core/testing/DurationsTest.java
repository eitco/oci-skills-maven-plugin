package de.eitco.cicd.ociskills.core.testing;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationsTest {

    private static final Duration DEFAULT = Duration.ofSeconds(7);

    @Test
    void parsesUnits() {
        assertThat(Durations.parse("500ms", DEFAULT)).isEqualTo(Duration.ofMillis(500));
        assertThat(Durations.parse("30s", DEFAULT)).isEqualTo(Duration.ofSeconds(30));
        assertThat(Durations.parse(" 5m ", DEFAULT)).isEqualTo(Duration.ofMinutes(5));
        assertThat(Durations.parse("1h", DEFAULT)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void returnsTheDefaultForMissingValues() {
        assertThat(Durations.parse(null, DEFAULT)).isEqualTo(DEFAULT);
        assertThat(Durations.parse(" ", DEFAULT)).isEqualTo(DEFAULT);
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> Durations.parse("30", DEFAULT)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'30'");
        assertThatThrownBy(() -> Durations.parse("-1s", DEFAULT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("1d", DEFAULT)).isInstanceOf(IllegalArgumentException.class);
    }
}
