package de.eitco.cicd.ociskills.core.registry;

/**
 * A reference to an image in a registry: {@code <registry>/<repository>:<tag>}.
 *
 * @param registry   the registry host, optionally with port
 * @param repository the repository path inside the registry, e.g. {@code docker-cicd-prerelease/eitco/cicd/nexus-test}
 * @param tag        the tag
 */
public record OciReference(String registry, String repository, String tag) {

    @Override
    public String toString() {
        return registry + "/" + repository + ":" + tag;
    }
}
