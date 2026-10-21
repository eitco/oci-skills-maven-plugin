package de.eitco.cicd.ociskills.core.skillcard;

/**
 * The values of a {@link SkillCard} that are not part of {@code SKILL.md} - they are the same for all skills of a
 * build.
 *
 * @param namespace the {@code metadata.namespace}
 * @param version   the {@code metadata.version}
 */
public record SkillCardSettings(String namespace, String version) {
}
