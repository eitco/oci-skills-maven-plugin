package de.eitco.cicd.ociskills.core.install;

import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

/**
 * Where an installed skill comes from - stored next to the skill to mark it as managed by the plugin.
 *
 * @param id      the skill
 * @param version the version of the skill
 * @param status  the status of the skill
 * @param digest  the digest of the image manifest
 * @param source  the id of the source the skill was fetched from
 */
public record SkillOrigin(SkillId id, String version, SkillStatus status, Digest digest, String source) {
}
