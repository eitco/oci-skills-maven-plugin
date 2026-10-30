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
 * @param repository    the repository the skill is stored in - {@code null} to leave it to the sources
 * @param location      the only source the skill is located in, given directly by the {@code <skill>} -
 *                      {@code null} to resolve it from the configured sources
 */
record SkillRequest(SkillId id, String versionSpec, SkillStatus minimumStatus, String source, String repository,
                    SkillSource location) {

    /**
     * A skill requested without repository.
     */
    SkillRequest(SkillId id, String versionSpec, SkillStatus minimumStatus, String source) {
        this(id, versionSpec, minimumStatus, source, null);
    }

    /**
     * A skill requested from the configured sources.
     */
    SkillRequest(SkillId id, String versionSpec, SkillStatus minimumStatus, String source, String repository) {
        this(id, versionSpec, minimumStatus, source, repository, null);
    }

    /**
     * A skill requested directly from a location.
     */
    static SkillRequest located(SkillId id, String versionSpec, SkillStatus minimumStatus, SkillSource location) {
        return new SkillRequest(id, versionSpec, minimumStatus, null, null, location);
    }
}
