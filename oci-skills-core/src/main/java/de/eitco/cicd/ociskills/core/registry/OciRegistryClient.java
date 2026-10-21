package de.eitco.cicd.ociskills.core.registry;

import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;

/**
 * Pushes an OCI image layout to a registry. Implementations upload missing blobs first, push the manifest under the
 * destination tag as last step and report the digest the registry confirms for the tag.
 */
public interface OciRegistryClient {

    PushResult push(OciImageLayout image, OciReference destination, RegistryCredentials credentials)
            throws SkillPushException;
}
