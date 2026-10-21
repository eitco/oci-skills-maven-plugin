package de.eitco.cicd.ociskills.core.oci;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The lifecycle status of a skill image that can be built by the plugin.
 */
public enum SkillStatus {
    DRAFT,
    TESTING,
    PUBLISHED;

    public static final String ANNOTATION = "io.skillimage.status";

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalArgumentException if the value is not a supported status
     */
    public static SkillStatus parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.value().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "status '" + value + "' is not supported, supported: " + supportedValues()));
    }

    public static String supportedValues() {
        return Arrays.stream(values()).map(SkillStatus::value).collect(Collectors.joining(", "));
    }
}
