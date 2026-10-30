package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
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
     * @param repository  the repository of the skill - {@code null} if it was requested without
     * @param versionSpec a version range like {@code [1.0,2.0)} or an exact version
     * @return the versions of the skill matching the spec in any order - regardless of their status
     * @throws SkillPullException also if the source cannot locate the skill, e.g. since it needs a repository
     */
    List<String> versions(SkillId id, String repository, String versionSpec) throws SkillPullException;

    /**
     * @param repository the repository of the skill - {@code null} if it was requested without
     * @param digest    the digest the image should have - {@code null} for any. Sources that cannot select images
     *                  by digest, like maven repositories, return the image they have - the caller verifies it.
     * @param directory an empty directory the image may be written to
     * @return the verified image - empty if the source does not have the skill in the given version and status
     */
    Optional<SkillImage> fetch(SkillId id, String repository, String version, SkillStatus status, Digest digest,
                               Path directory) throws SkillPullException;
}
