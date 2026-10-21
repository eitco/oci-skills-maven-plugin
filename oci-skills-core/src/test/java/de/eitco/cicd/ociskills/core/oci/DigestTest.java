package de.eitco.cicd.ociskills.core.oci;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.MessageDigest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigestTest {

    @Test
    void formatsSha256() throws Exception {
        Digest digest = Digest.sha256(MessageDigest.getInstance("SHA-256").digest("abc".getBytes(UTF_8)));

        assertThat(digest).hasToString("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(digest.hex()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void parsesAndEquals() {
        String value = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        assertThat(Digest.parse(value)).isEqualTo(Digest.parse(value)).hasToString(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ba7816bf", "sha512:ab", "sha256:XYZ", "sha256:../../etc/passwd"})
    void rejectsInvalidDigests(String value) {
        assertThatThrownBy(() -> Digest.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
