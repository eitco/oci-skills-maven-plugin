package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A repository skills are pulled from - a maven repository or an OCI registry.
 */
interface SkillSource {

    /**
     * @return the id of the source as configured
     */
    String id();

    /**
     * @param versionSpec a version range like {@code [1.0,2.0)} or an exact version
     * @return the versions of the skill matching the spec in any order - regardless of their status
     */
    List<String> versions(SkillId id, String versionSpec) throws SkillPullException;

    /**
     * @param digest    the digest the image should have - {@code null} for any. Sources that cannot select images
     *                  by digest, like maven repositories, return the image they have - the caller verifies it.
     * @param directory an empty directory the image layout may be written to
     * @return the image layout - empty if the source does not have the skill in the given version and status
     */
    Optional<OciImageLayout> fetch(SkillId id, String version, SkillStatus status, Digest digest, Path directory)
            throws SkillPullException;
}
