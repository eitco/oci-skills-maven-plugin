package de.eitco.cicd.ociskills.core.testing;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats durations like {@code 500ms}, {@code 30s}, {@code 5m} or {@code 1h}.
 */
public final class Durations {

    private static final Pattern DURATION = Pattern.compile("(\\d+)(ms|s|m|h)");

    private Durations() {
    }

    /**
     * @param value        the duration - a number followed by {@code ms}, {@code s}, {@code m} or {@code h}
     * @param defaultValue returned if {@code value} is {@code null} or blank
     * @throws IllegalArgumentException if the value is not a valid duration
     */
    public static Duration parse(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        Matcher matcher = DURATION.matcher(value.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid duration '" + value
                    + "' - expected a number followed by ms, s, m or h, e.g. 30s");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            default -> Duration.ofHours(amount);
        };
    }

    /**
     * @return the duration in the largest unit it can be expressed in exactly, e.g. {@code 90s} or {@code 2m}
     */
    public static String format(Duration duration) {
        long millis = duration.toMillis();
        if (millis % 1000 != 0) {
            return millis + "ms";
        }
        long seconds = millis / 1000;
        if (seconds % 3600 == 0 && seconds > 0) {
            return seconds / 3600 + "h";
        }
        if (seconds % 60 == 0 && seconds > 0) {
            return seconds / 60 + "m";
        }
        return seconds + "s";
    }
}
