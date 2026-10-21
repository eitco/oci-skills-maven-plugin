package de.eitco.cicd.ociskills.core.oci;

import java.util.List;
import java.util.Map;

/**
 * Creates the OCI image configuration of a skill image. For compatibility with {@code skillctl} the platform is
 * always {@code linux/amd64}.
 */
public class ImageConfigBuilder {

    public static final String ARCHITECTURE = "amd64";
    public static final String OS = "linux";
    public static final String ROOTFS_TYPE = "layers";

    public OciImageConfig build(Digest diffId) {
        return new OciImageConfig(ARCHITECTURE, OS, Map.of(), new OciImageConfig.RootFs(ROOTFS_TYPE, List.of(diffId)));
    }
}
