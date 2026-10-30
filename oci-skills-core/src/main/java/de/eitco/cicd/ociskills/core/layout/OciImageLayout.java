package de.eitco.cicd.ociskills.core.layout;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.layer.TarExtractor;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * An OCI image layout containing a single skill image.
 *
 * @param directory          the layout directory
 * @param manifestDescriptor the descriptor of the manifest as listed in {@code index.json}
 * @param manifest           the parsed manifest
 * @param tag                the reference name ({@code org.opencontainers.image.ref.name}) of the manifest
 */
public record OciImageLayout(Path directory, OciDescriptor manifestDescriptor, OciImageManifest manifest, String tag)
        implements SkillImage {

    public static final String OCI_LAYOUT_FILE = "oci-layout";
    public static final String INDEX_FILE = "index.json";
    public static final String BLOBS_DIRECTORY = "blobs";

    public Path blob(OciDescriptor descriptor) {
        return directory.resolve(BLOBS_DIRECTORY)
                .resolve(descriptor.digest().algorithm())
                .resolve(descriptor.digest().hex());
    }

    /**
     * Extracts the gzip compressed tar layer.
     */
    @Override
    public void extract(Path target) throws SkillBuildException {
        List<OciDescriptor> layers = manifest.layers();
        if (layers == null || layers.size() != 1 || !MediaTypes.IMAGE_LAYER_TAR_GZIP.equals(layers.get(0).mediaType())) {
            throw new SkillBuildException("the image " + manifestDescriptor.digest() + " must have exactly one layer "
                    + "of type " + MediaTypes.IMAGE_LAYER_TAR_GZIP);
        }
        try (InputStream layer = new GZIPInputStream(new BufferedInputStream(
                Files.newInputStream(blob(layers.get(0)))))) {
            new TarExtractor().extract(layer, target);
        } catch (IOException e) {
            throw new SkillBuildException("extracting the layer of " + manifestDescriptor.digest() + " into " + target
                    + " failed: " + e, e);
        }
    }
}
