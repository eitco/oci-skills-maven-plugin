package de.eitco.cicd.ociskills.core.oci;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.eitco.cicd.ociskills.core.SkillBuildException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Serializes OCI structures deterministically: stable property order, map entries sorted by key, no pretty printing.
 * Non ASCII characters are escaped, so the bytes do not depend on any platform charset on their way to a registry.
 * The bytes returned are exactly the bytes to store and to hash.
 */
public final class OciJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private OciJson() {
    }

    public static byte[] serialize(Object value) {
        return MAPPER.writeValueAsBytes(value);
    }

    public static <T> T deserialize(byte[] json, Class<T> type) throws SkillBuildException {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new SkillBuildException("invalid " + type.getSimpleName() + " json: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Serializes the given value and creates a descriptor for the serialized bytes.
     */
    public static SerializedBlob serializeBlob(Object value, String mediaType) {
        byte[] bytes = serialize(value);
        return new SerializedBlob(bytes, new OciDescriptor(mediaType, Digest.sha256(sha256().digest(bytes)),
                bytes.length));
    }

    /**
     * @return a new SHA-256 message digest
     */
    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not supported by this JVM", e);
        }
    }
}
