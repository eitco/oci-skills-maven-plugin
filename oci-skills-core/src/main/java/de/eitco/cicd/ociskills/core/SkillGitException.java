package de.eitco.cicd.ociskills.core;

/**
 * Signals an error while publishing skills to a git repository. Messages must never contain credentials.
 */
public class SkillGitException extends SkillException {

    public SkillGitException(String message, Throwable cause) {
        super(Phase.PUSH, message, cause);
    }

    public SkillGitException(String message) {
        this(message, null);
    }
}
