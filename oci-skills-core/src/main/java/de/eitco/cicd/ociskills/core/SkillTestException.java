package de.eitco.cicd.ociskills.core;

/**
 * Signals a technical error while testing or evaluating a skill - not a failing test.
 */
public class SkillTestException extends SkillException {

    public SkillTestException(String message, Throwable cause) {
        super(Phase.TEST, message, cause);
    }

    public SkillTestException(String message) {
        this(message, null);
    }
}
