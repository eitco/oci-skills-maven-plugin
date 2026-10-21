package de.eitco.cicd.ociskills.core.skillcard;

/**
 * The {@code metadata} section of a {@link SkillCard}.
 */
public record SkillMetadata(String name, String namespace, String version, String description) {
}
