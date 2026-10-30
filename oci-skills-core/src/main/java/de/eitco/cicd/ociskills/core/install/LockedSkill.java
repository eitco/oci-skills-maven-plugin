package de.eitco.cicd.ociskills.core.install;

import de.eitco.cicd.ociskills.core.oci.SkillStatus;

/**
 * An entry of the lock file: what was requested and what it was resolved to.
 *
 * @param requestedVersion    the version or version range requested
 * @param requestedStatus     the least mature status accepted
 * @param requestedRepository the repository the skill was requested from - {@code null} if none was given
 * @param requestedLocation   the id of the location the skill was requested from directly, e.g.
 *                            {@code <groupId>:<artifactId>:<classifier>} - {@code null} if it was requested from the
 *                            sources
 * @param resolved            the skill the request was resolved to
 */
public record LockedSkill(String requestedVersion, SkillStatus requestedStatus, String requestedRepository,
                          String requestedLocation, SkillOrigin resolved) {

    /**
     * A skill requested without repository.
     */
    public LockedSkill(String requestedVersion, SkillStatus requestedStatus, SkillOrigin resolved) {
        this(requestedVersion, requestedStatus, null, resolved);
    }

    /**
     * A skill requested from the sources.
     */
    public LockedSkill(String requestedVersion, SkillStatus requestedStatus, String requestedRepository,
                       SkillOrigin resolved) {
        this(requestedVersion, requestedStatus, requestedRepository, null, resolved);
    }

    public SkillId id() {
        return resolved.id();
    }
}
