package de.eitco.cicd.ociskills.core.layout;

import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;

import java.nio.file.Path;

/**
 * An OCI image layout containing a single skill image.
 *
 * @param directory          the layout directory
 * @param manifestDescriptor the descriptor of the manifest as listed in {@code index.json}
 * @param manifest           the parsed manifest
 * @param tag                the reference name ({@code org.opencontainers.image.ref.name}) of the manifest
 */
public record OciImageLayout(Path directory, OciDescriptor manifestDescriptor, OciImageManifest manifest, String tag) {

    public static final String OCI_LAYOUT_FILE = "oci-layout";
    public static final String INDEX_FILE = "index.json";
    public static final String BLOBS_DIRECTORY = "blobs";

    public Path blob(OciDescriptor descriptor) {
        return directory.resolve(BLOBS_DIRECTORY)
                .resolve(descriptor.digest().algorithm())
                .resolve(descriptor.digest().hex());
    }
}
