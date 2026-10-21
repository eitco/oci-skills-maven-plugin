package de.eitco.cicd.ociskills.core.oci;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * An OCI content descriptor.
 */
@JsonPropertyOrder({"mediaType", "digest", "size", "annotations"})
public record OciDescriptor(
        String mediaType,
        Digest digest,
        long size,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> annotations) {

    public OciDescriptor(String mediaType, Digest digest, long size) {
        this(mediaType, digest, size, null);
    }

    public OciDescriptor withAnnotations(Map<String, String> annotations) {
        return new OciDescriptor(mediaType, digest, size, annotations);
    }
}
