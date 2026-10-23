package de.eitco.cicd.ociskills.core.testing;

import java.util.Objects;

/**
 * A problem found by {@link StaticSkillChecks}.
 *
 * @param check    the name of the check, e.g. {@code links}
 * @param severity whether the problem fails the check
 * @param message  the problem
 */
public record Finding(String check, Severity severity, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public Finding {
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public static Finding error(String check, String message) {
        return new Finding(check, Severity.ERROR, message);
    }

    public static Finding warning(String check, String message) {
        return new Finding(check, Severity.WARNING, message);
    }
}
