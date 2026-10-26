package de.eitco.cicd.ociskills.core.registry;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Pushes an OCI image layout to a registry and pulls it back. Implementations upload missing blobs first, push the
 * manifest under the destination tag as last step and report the digest the registry confirms for the tag.
 */
public interface OciRegistryClient {

    PushResult push(OciImageLayout image, OciReference destination, RegistryCredentials credentials)
            throws SkillPushException;

    /**
     * @param repository the repository, e.g. {@code nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test} -
     *                   the tag of the reference is ignored
     * @return the tags of the repository - empty if the repository does not exist
     */
    List<String> tags(OciReference repository, RegistryCredentials credentials) throws SkillPullException;

    /**
     * Pulls an image into an OCI image layout. All blobs are verified against their digests.
     *
     * @param source    the image - its tag is the tag of the layout
     * @param digest    the digest of the manifest to pull - {@code null} to pull the manifest the tag points to
     * @param directory the directory to write the layout to
     * @return the layout - empty if the image does not exist
     * @throws SkillPullException if the image is no skill image or a digest does not match
     */
    Optional<OciImageLayout> pull(OciReference source, Digest digest, Path directory, RegistryCredentials credentials)
            throws SkillPullException;
}
