package de.eitco.cicd.ociskills.core.oci;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * An OCI image manifest.
 */
@JsonPropertyOrder({"schemaVersion", "mediaType", "config", "layers", "annotations"})
public record OciImageManifest(
        int schemaVersion,
        String mediaType,
        OciDescriptor config,
        List<OciDescriptor> layers,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> annotations) {
}
