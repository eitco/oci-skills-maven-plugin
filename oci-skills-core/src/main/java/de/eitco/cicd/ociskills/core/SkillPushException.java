package de.eitco.cicd.ociskills.core;

import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.registry.OciReference;

/**
 * Signals an error while pushing an image to a registry. The message names the target reference and - if known -
 * the affected descriptor, but never any credentials.
 */
public class SkillPushException extends SkillException {

    /**
     * The cause of a push error, used to give the user a precise hint on what went wrong.
     */
    public enum Reason {
        REGISTRY_UNREACHABLE("registry not reachable"),
        TLS_UNTRUSTED("TLS certificate of registry not trusted"),
        AUTHENTICATION_FAILED("authentication failed"),
        PERMISSION_DENIED("no permission to push"),
        REPOSITORY_NOT_FOUND("repository or routing not correct"),
        MEDIA_TYPE_REJECTED("registry rejected media type"),
        DIGEST_MISMATCH("digest confirmed by registry differs from local digest"),
        INVALID_LAYOUT("local OCI image layout is invalid"),
        INVALID_CONFIGURATION("invalid registry connection configuration"),
        UNKNOWN("push failed");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final Reason reason;

    public SkillPushException(Reason reason, OciReference target, OciDescriptor descriptor, String detail, Throwable cause) {
        super(Phase.PUSH, message(reason, target, descriptor, detail), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static String message(Reason reason, OciReference target, OciDescriptor descriptor, String detail) {
        StringBuilder message = new StringBuilder(reason.description());
        if (target != null) {
            message.append(" - target: ").append(target);
        }
        if (descriptor != null) {
            message.append(" - descriptor: ").append(descriptor.mediaType()).append(' ').append(descriptor.digest());
        }
        if (detail != null && !detail.isBlank()) {
            message.append(" - ").append(detail);
        }
        return message.toString();
    }
}
