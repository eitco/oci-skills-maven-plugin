package de.eitco.cicd.ociskills.core.oci;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * The OCI image configuration of a skill image.
 */
@JsonPropertyOrder({"architecture", "os", "config", "rootfs"})
public record OciImageConfig(String architecture, String os, Map<String, Object> config, RootFs rootfs) {

    /**
     * The {@code rootfs} section of the image configuration.
     */
    @JsonPropertyOrder({"type", "diff_ids"})
    public record RootFs(String type, @JsonProperty("diff_ids") List<Digest> diffIds) {
    }
}
