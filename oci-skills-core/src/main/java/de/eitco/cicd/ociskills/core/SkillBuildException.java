package de.eitco.cicd.ociskills.core;

/**
 * Signals a technical error while creating the OCI image layout.
 */
public class SkillBuildException extends SkillException {

    public SkillBuildException(String message, Throwable cause) {
        super(Phase.BUILD, message, cause);
    }

    public SkillBuildException(String message) {
        this(message, null);
    }
}
