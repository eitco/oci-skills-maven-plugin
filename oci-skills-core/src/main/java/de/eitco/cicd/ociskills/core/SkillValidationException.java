package de.eitco.cicd.ociskills.core;

import java.util.List;

/**
 * Signals that the skill directory or its SkillCard is invalid. Validation errors are always raised before any
 * OCI content is written.
 */
public class SkillValidationException extends SkillException {

    private final List<String> problems;

    public SkillValidationException(List<String> problems) {
        super(Phase.VALIDATION, "skill validation failed:\n  - " + String.join("\n  - ", problems), null);
        this.problems = List.copyOf(problems);
    }

    public SkillValidationException(String problem) {
        this(List.of(problem));
    }

    public List<String> problems() {
        return problems;
    }
}
