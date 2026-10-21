package de.eitco.cicd.ociskills.core;

/**
 * Base class of all errors reported while building or pushing a skill image. Every error knows the {@link Phase}
 * it occurred in. Messages must never contain credentials.
 */
public abstract class SkillException extends Exception {

    private final Phase phase;

    protected SkillException(Phase phase, String message, Throwable cause) {
        super("[" + phase.displayName() + "] " + message, cause);
        this.phase = phase;
    }

    public Phase phase() {
        return phase;
    }
}
