package de.eitco.cicd.ociskills.core.oci;

/**
 * Determines the tag of a skill image.
 */
public interface TagStrategy {

    String tag(String version, SkillStatus status);
}
