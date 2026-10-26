package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

/**
 * A skill requested by the project.
 *
 * @param id            the skill
 * @param versionSpec   a version range like {@code [1.0,2.0)} or an exact version
 * @param minimumStatus the least mature status accepted
 * @param source        the id of the only source to resolve the skill from - {@code null} for all
 */
record SkillRequest(SkillId id, String versionSpec, SkillStatus minimumStatus, String source) {
}
