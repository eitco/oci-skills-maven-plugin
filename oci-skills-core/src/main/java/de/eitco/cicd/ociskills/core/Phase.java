package de.eitco.cicd.ociskills.core;

/**
 * The phase in which a {@link SkillException} occurred.
 */
public enum Phase {
    VALIDATION("validation"),
    BUILD("build"),
    PUSH("push");

    private final String displayName;

    Phase(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
