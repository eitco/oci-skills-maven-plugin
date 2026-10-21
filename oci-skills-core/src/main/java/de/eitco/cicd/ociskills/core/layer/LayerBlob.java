package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;

import java.nio.file.Path;

/**
 * The compressed skill layer written to disk.
 *
 * @param file       the gzip compressed tar file
 * @param descriptor the descriptor of the compressed layer
 * @param diffId     the digest of the uncompressed tar stream
 */
public record LayerBlob(Path file, OciDescriptor descriptor, Digest diffId) {
}
