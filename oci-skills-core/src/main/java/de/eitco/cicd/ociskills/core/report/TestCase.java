package de.eitco.cicd.ociskills.core.report;

import java.time.Duration;
import java.util.Objects;

/**
 * The result of a single test case.
 *
 * @param name     the name of the test case
 * @param status   the outcome
 * @param duration how long the test case took
 * @param message  why the test case failed or was skipped - {@code null} if it passed
 * @param output   further output to show in the report, e.g. warnings or the output of a script - may be {@code null}
 */
public record TestCase(String name, TestStatus status, Duration duration, String message, String output) {

    public TestCase {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        duration = duration == null ? Duration.ZERO : duration;
    }

    public static TestCase passed(String name, Duration duration, String output) {
        return new TestCase(name, TestStatus.PASSED, duration, null, output);
    }

    public static TestCase failed(String name, Duration duration, String message, String output) {
        return new TestCase(name, TestStatus.FAILED, duration, message, output);
    }

    public static TestCase skipped(String name, String reason) {
        return new TestCase(name, TestStatus.SKIPPED, Duration.ZERO, reason, null);
    }
}
