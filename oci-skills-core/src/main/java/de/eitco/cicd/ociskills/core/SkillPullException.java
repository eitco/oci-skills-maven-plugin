package de.eitco.cicd.ociskills.core;

/**
 * Signals an error while resolving, fetching or installing a skill.
 */
public class SkillPullException extends SkillException {

    public SkillPullException(String message, Throwable cause) {
        super(Phase.PULL, message, cause);
    }

    public SkillPullException(String message) {
        this(message, null);
    }
}
