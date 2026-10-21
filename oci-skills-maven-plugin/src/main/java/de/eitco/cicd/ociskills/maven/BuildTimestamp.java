package de.eitco.cicd.ociskills.maven;

import org.apache.maven.plugin.MojoFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Determines the timestamp of a skill image build.
 */
final class BuildTimestamp {

    private BuildTimestamp() {
    }

    /**
     * @param reproducible    whether the build must be reproducible
     * @param outputTimestamp the value of {@code project.build.outputTimestamp} - ISO-8601 or seconds since epoch
     * @param clock           the clock to use for non reproducible builds
     * @throws MojoFailureException if the build is reproducible and the timestamp is missing or invalid
     */
    static Instant resolve(boolean reproducible, String outputTimestamp, Clock clock) throws MojoFailureException {
        if (!reproducible) {
            return clock.instant();
        }
        if (outputTimestamp == null || outputTimestamp.isBlank()) {
            throw new MojoFailureException("project.build.outputTimestamp is not set - it is required for "
                    + "reproducible skill images. Set the property, e.g. "
                    + "<project.build.outputTimestamp>2026-01-01T00:00:00Z</project.build.outputTimestamp>, "
                    + "or set <reproducible>false</reproducible> to use the current time.");
        }
        String value = outputTimestamp.trim();
        try {
            if (value.chars().allMatch(Character::isDigit)) {
                return Instant.ofEpochSecond(Long.parseLong(value));
            }
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new MojoFailureException("project.build.outputTimestamp '" + value + "' is neither an ISO-8601 "
                    + "timestamp nor seconds since epoch");
        }
    }
}
