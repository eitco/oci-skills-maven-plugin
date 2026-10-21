package de.eitco.cicd.ociskills.core.registry;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Creates the push target {@code <registry>/<repository>/<name>:<tag>}.
 */
public class OciReferenceFactory {

    /**
     * A path component as defined by the OCI distribution specification.
     */
    private static final Pattern PATH_COMPONENT = Pattern.compile("[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*");
    private static final Pattern REGISTRY = Pattern.compile("[A-Za-z0-9.-]+(:[0-9]+)?");

    /**
     * @param registry   the registry host, optionally with port
     * @param repository the repository path the skill is pushed below
     * @param name       the skill name ({@code metadata.name})
     * @param tag        the tag created by the tag strategy
     * @throws IllegalArgumentException if registry or repository are missing or invalid
     */
    public OciReference create(String registry, String repository, String name, String tag) {
        if (registry == null || registry.isBlank()) {
            throw new IllegalArgumentException("registry is missing");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository is missing");
        }
        String host = registry.trim().replaceFirst("^[a-zA-Z]+://", "").replaceAll("/+$", "");
        if (!REGISTRY.matcher(host).matches()) {
            throw new IllegalArgumentException("registry '" + registry + "' is not a valid host[:port]");
        }
        String path = Arrays.stream((repository.trim() + "/" + name).split("/"))
                .filter(component -> !component.isEmpty())
                .collect(Collectors.joining("/"));
        for (String component : path.split("/")) {
            if (!PATH_COMPONENT.matcher(component).matches()) {
                throw new IllegalArgumentException("repository '" + repository + "' contains the invalid path "
                        + "component '" + component + "' - only lower case alphanumerics separated by '.', '_' or "
                        + "'-' are allowed");
            }
        }
        return new OciReference(host, path, tag);
    }
}
