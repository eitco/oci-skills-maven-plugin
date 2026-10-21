package de.eitco.cicd.ociskills.core.registry;

import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;

import java.util.List;

/**
 * The result of pushing an image.
 *
 * @param destination     the reference the image was pushed to
 * @param confirmedDigest the manifest digest the registry reports for the tag after the push
 * @param uploadedBlobs   the blobs that were uploaded
 * @param skippedBlobs    the blobs that already existed in the registry
 */
public record PushResult(OciReference destination, Digest confirmedDigest, List<OciDescriptor> uploadedBlobs,
                         List<OciDescriptor> skippedBlobs) {
}
