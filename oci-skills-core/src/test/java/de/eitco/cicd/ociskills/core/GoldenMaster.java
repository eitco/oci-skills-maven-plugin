package de.eitco.cicd.ociskills.core;

import de.eitco.cicd.ociskills.core.oci.Digest;

import java.nio.file.Path;
import java.time.Instant;

/**
 * The reference image built with skillctl 0.7.2 - see {@code src/test/resources/golden-master/skillctl-0.7.2}.
 * The files are accessed directly from the source tree, since resource copying loses file permissions.
 */
public final class GoldenMaster {

    public static final Path ROOT = Path.of("src/test/resources/golden-master/skillctl-0.7.2");
    public static final Path SOURCE = ROOT.resolve("nexus-test");
    public static final Path LAYOUT = ROOT.resolve("layout");

    public static final Digest MANIFEST_DIGEST =
            Digest.parse("sha256:4655ac428ce39fa6f590530e90343ff248a7886e9ccfe04c7da378027cff80da");
    public static final long MANIFEST_SIZE = 736;
    public static final Digest CONFIG_DIGEST =
            Digest.parse("sha256:92f7a62db6fd151aed0b1a9ca0b56dfeb1baaca19f7ffa8782f46e8043224852");
    public static final long CONFIG_SIZE = 163;
    public static final Digest LAYER_DIGEST =
            Digest.parse("sha256:e177f16a2d4bd24490150c8262a30dafb0da2e4f157417811601b27dbb7e85c1");
    public static final long LAYER_SIZE = 472;
    public static final Digest DIFF_ID =
            Digest.parse("sha256:0c04b4999d0ef80aed95205fc771096dc34f59bbda7ee1309bd93a377f027c2b");
    public static final Instant CREATED = Instant.parse("2026-10-20T13:47:12Z");

    private GoldenMaster() {
    }

    public static Path blob(Digest digest) {
        return LAYOUT.resolve("blobs").resolve(digest.algorithm()).resolve(digest.hex());
    }
}
