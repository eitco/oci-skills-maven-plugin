package de.eitco.cicd.ociskills.core.oci;

/**
 * The OCI media types used by skill images.
 */
public final class MediaTypes {

    public static final String IMAGE_INDEX = "application/vnd.oci.image.index.v1+json";
    public static final String IMAGE_MANIFEST = "application/vnd.oci.image.manifest.v1+json";
    public static final String IMAGE_CONFIG = "application/vnd.oci.image.config.v1+json";
    public static final String IMAGE_LAYER_TAR_GZIP = "application/vnd.oci.image.layer.v1.tar+gzip";

    private MediaTypes() {
    }
}
