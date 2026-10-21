package de.eitco.cicd.ociskills.core.oci;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * An OCI image index - used as {@code index.json} of an OCI image layout.
 */
@JsonPropertyOrder({"schemaVersion", "mediaType", "manifests"})
public record OciImageIndex(int schemaVersion, String mediaType, List<OciDescriptor> manifests) {
}
