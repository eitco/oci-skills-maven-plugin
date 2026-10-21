package de.eitco.cicd.ociskills.core.oci;

import de.eitco.cicd.ociskills.core.GoldenMaster;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImageConfigBuilderTest {

    private final ImageConfigBuilder builder = new ImageConfigBuilder();

    @Test
    void createsLinuxAmd64ConfigWithDiffId() {
        OciImageConfig config = builder.build(GoldenMaster.DIFF_ID);

        assertThat(config.architecture()).isEqualTo("amd64");
        assertThat(config.os()).isEqualTo("linux");
        assertThat(config.config()).isEqualTo(Map.of());
        assertThat(config.rootfs()).isEqualTo(new OciImageConfig.RootFs("layers", List.of(GoldenMaster.DIFF_ID)));
    }

    @Test
    void serializesByteIdenticalToSkillctl() throws Exception {
        SerializedBlob blob = OciJson.serializeBlob(builder.build(GoldenMaster.DIFF_ID), MediaTypes.IMAGE_CONFIG);

        assertThat(blob.bytes()).isEqualTo(Files.readAllBytes(GoldenMaster.blob(GoldenMaster.CONFIG_DIGEST)));
        assertThat(blob.descriptor()).isEqualTo(
                new OciDescriptor(MediaTypes.IMAGE_CONFIG, GoldenMaster.CONFIG_DIGEST, GoldenMaster.CONFIG_SIZE));
    }
}
