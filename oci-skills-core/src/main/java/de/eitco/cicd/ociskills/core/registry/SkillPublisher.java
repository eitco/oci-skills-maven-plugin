package de.eitco.cicd.ociskills.core.registry;

import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;

/**
 * Pushes a skill image and optionally verifies the digest confirmed by the registry against the local manifest
 * digest.
 */
public class SkillPublisher {

    private final OciRegistryClient client;

    public SkillPublisher(OciRegistryClient client) {
        this.client = client;
    }

    /**
     * @param verifyDigest whether to fail if the digest confirmed by the registry differs from the local digest
     */
    public PushResult publish(OciImageLayout image, OciReference destination, RegistryCredentials credentials,
                              boolean verifyDigest) throws SkillPushException {
        PushResult result = client.push(image, destination, credentials);
        if (verifyDigest && !image.manifestDescriptor().digest().equals(result.confirmedDigest())) {
            throw new SkillPushException(SkillPushException.Reason.DIGEST_MISMATCH, destination,
                    image.manifestDescriptor(), "local " + image.manifestDescriptor().digest() + ", registry "
                    + (result.confirmedDigest() == null ? "<none>" : result.confirmedDigest()), null);
        }
        return result;
    }
}
