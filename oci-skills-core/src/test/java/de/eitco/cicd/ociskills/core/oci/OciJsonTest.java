package de.eitco.cicd.ociskills.core.oci;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class OciJsonTest {

    private static final Digest DIGEST =
            Digest.parse("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    @Test
    void serializesCompactWithStablePropertyOrder() {
        byte[] json = OciJson.serialize(new OciDescriptor("text/plain", DIGEST, 3));

        assertThat(new String(json, UTF_8)).isEqualTo(
                "{\"mediaType\":\"text/plain\",\"digest\":\"" + DIGEST + "\",\"size\":3}");
    }

    @Test
    void sortsMapEntriesByKey() {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("b", "2");
        annotations.put("a", "1");

        byte[] json = OciJson.serialize(new OciDescriptor("text/plain", DIGEST, 3, annotations));

        assertThat(new String(json, UTF_8)).endsWith("\"annotations\":{\"a\":\"1\",\"b\":\"2\"}}");
    }

    @Test
    void escapesNonAsciiCharactersSoThatBytesAreIndependentOfPlatformCharsets() {
        byte[] json = OciJson.serialize(new OciDescriptor("text/plain", DIGEST, 3, Map.of("d", "Grüße")));

        assertThat(new String(json, UTF_8)).endsWith("\"annotations\":{\"d\":\"Gr\\u00FC\\u00DFe\"}}");
        for (byte value : json) {
            assertThat(value).isNotNegative();
        }
    }

    @Test
    void producesIdenticalBytesForEqualValues() {
        OciImageIndex index = new OciImageIndex(2, MediaTypes.IMAGE_INDEX,
                List.of(new OciDescriptor(MediaTypes.IMAGE_MANIFEST, DIGEST, 10, Map.of("x", "y", "a", "b"))));

        assertThat(OciJson.serialize(index)).isEqualTo(OciJson.serialize(index));
    }

    @Test
    void roundTrips() throws Exception {
        OciImageIndex index = new OciImageIndex(2, MediaTypes.IMAGE_INDEX,
                List.of(new OciDescriptor(MediaTypes.IMAGE_MANIFEST, DIGEST, 10, Map.of("x", "y"))));

        assertThat(OciJson.deserialize(OciJson.serialize(index), OciImageIndex.class)).isEqualTo(index);
    }

    @Test
    void ignoresUnknownPropertiesWhenDeserializing() throws Exception {
        String json = "{\"mediaType\":\"text/plain\",\"digest\":\"" + DIGEST + "\",\"size\":3,\"urls\":[\"x\"]}";

        assertThat(OciJson.deserialize(json.getBytes(UTF_8), OciDescriptor.class))
                .isEqualTo(new OciDescriptor("text/plain", DIGEST, 3));
    }

    @Test
    void createsDescriptorOfSerializedBytes() throws Exception {
        SerializedBlob blob = OciJson.serializeBlob(new OciDescriptor("text/plain", DIGEST, 3), "application/json");

        assertThat(blob.descriptor().mediaType()).isEqualTo("application/json");
        assertThat(blob.descriptor().size()).isEqualTo(blob.bytes().length);
        assertThat(blob.descriptor().digest())
                .isEqualTo(Digest.sha256(MessageDigest.getInstance("SHA-256").digest(blob.bytes())));
    }
}
