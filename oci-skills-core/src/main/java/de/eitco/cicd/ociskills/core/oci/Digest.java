package de.eitco.cicd.ociskills.core.oci;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A content digest in the form {@code sha256:<hex>}.
 */
public final class Digest {

    public static final String SHA_256 = "sha256";

    private static final Pattern SHA_256_HEX = Pattern.compile("[a-f0-9]{64}");

    private final String algorithm;
    private final String hex;

    private Digest(String algorithm, String hex) {
        this.algorithm = algorithm;
        this.hex = hex;
    }

    public static Digest sha256(byte[] digestBytes) {
        return new Digest(SHA_256, HexFormat.of().formatHex(digestBytes));
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Digest parse(String digest) {
        int separator = digest.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("invalid digest '" + digest + "'");
        }
        String algorithm = digest.substring(0, separator);
        String hex = digest.substring(separator + 1);
        if (!SHA_256.equals(algorithm) || !SHA_256_HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException("unsupported digest '" + digest + "' - only sha256 is supported");
        }
        return new Digest(algorithm, hex);
    }

    public String algorithm() {
        return algorithm;
    }

    public String hex() {
        return hex;
    }

    @JsonValue
    @Override
    public String toString() {
        return algorithm + ":" + hex;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Digest digest && algorithm.equals(digest.algorithm) && hex.equals(digest.hex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, hex);
    }
}
