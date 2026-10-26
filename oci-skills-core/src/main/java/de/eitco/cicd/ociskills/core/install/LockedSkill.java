package de.eitco.cicd.ociskills.core.install;

import de.eitco.cicd.ociskills.core.oci.SkillStatus;

/**
 * An entry of the lock file: what was requested and what it was resolved to.
 *
 * @param requestedVersion the version or version range requested
 * @param requestedStatus  the least mature status accepted
 * @param resolved         the skill the request was resolved to
 */
public record LockedSkill(String requestedVersion, SkillStatus requestedStatus, SkillOrigin resolved) {

    public SkillId id() {
        return resolved.id();
    }
}
