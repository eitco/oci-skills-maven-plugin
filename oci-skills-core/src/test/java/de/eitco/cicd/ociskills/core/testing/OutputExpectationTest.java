package de.eitco.cicd.ociskills.core.testing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputExpectationTest {

    @Test
    void holdsForMatchingOutput() {
        OutputExpectation expectation = new OutputExpectation(List.of("Hello"), List.of("^Hello, world$"),
                List.of("error"), false);

        assertThat(expectation.violations("stdout", "first line\nHello, world\n")).isEmpty();
    }

    @Test
    void reportsEveryViolation() {
        OutputExpectation expectation = new OutputExpectation(List.of("Hello"), List.of("^Bye$"),
                List.of("err.r"), null);

        assertThat(expectation.violations("stdout", "an error"))
                .containsExactly("stdout does not contain 'Hello'", "stdout does not match /^Bye$/",
                        "stdout matches /err.r/");
    }

    @Test
    void checksEmptiness() {
        assertThat(new OutputExpectation(null, null, null, true).violations("stderr", "oops"))
                .containsExactly("stderr is not empty");
        assertThat(new OutputExpectation(null, null, null, false).violations("stderr", ""))
                .containsExactly("stderr is empty");
        assertThat(new OutputExpectation(null, null, null, null).violations("stderr", "")).isEmpty();
    }
}
