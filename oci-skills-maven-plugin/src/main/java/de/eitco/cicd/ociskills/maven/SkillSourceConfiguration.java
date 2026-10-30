package de.eitco.cicd.ociskills.maven;

import java.io.File;
import java.util.List;

/**
 * The configuration of a {@code <source>} skills are pulled from. {@code groupId}, {@code artifactId} and
 * {@code repository} may contain the placeholders {@code {namespace}} and {@code {name}} of the skill as well as
 * {@code {repository}}, {@code {repository.parent}} and {@code {repository.last}} derived from its
 * {@code <repository>} - see {@link SourceTemplate}.
 * <ul>
 *     <li>{@code maven}: the skill is the artifact {@code <groupId>:<artifactId>:tar:<classifier>} attached by
 *     {@code oci-skills:archive}. The repositories of the project are used.</li>
 *     <li>{@code oci}: the skill is the image {@code <registry>/<repository>/<name>} pushed by
 *     {@code oci-skills:push} - the {@code <repository>} of the skill, if it has one.</li>
 * </ul>
 */
public class SkillSourceConfiguration {

    static final String MAVEN = "maven";
    static final String OCI = "oci";

    /**
     * The id of the source - used in the lock file and to pin skills to the source.
     */
    private String id;

    /**
     * {@code maven} or {@code oci}.
     */
    private String type;

    /**
     * {@code maven} only: the group id of the artifact, e.g. {@code com.example.{repository.parent}}.
     */
    private String groupId;

    /**
     * {@code maven} only: the artifact id of the artifact, e.g. {@code {repository.last}}.
     */
    private String artifactId;

    /**
     * {@code oci} only: the registry host, optionally with port, e.g. {@code registry.example.com}.
     */
    private String registry;

    /**
     * {@code oci} only: the repository path below which skills without {@code <repository>} are located, e.g.
     * {@code skills/{namespace}}. The skill name is appended. Without it, every skill needs a
     * {@code <repository>}.
     */
    private String repository;

    /**
     * {@code oci} only: the id of the {@code <server>} in the maven settings holding the registry credentials.
     * Without a server id the registry is accessed anonymously.
     */
    private String registryServerId;

    /**
     * {@code oci} only: PEM files of additional certificate authorities to trust for the registry connection.
     */
    private List<File> caCertificates;

    /**
     * {@code oci} only: whether to connect to the registry via plain http instead of https. Only meant for local
     * test registries.
     */
    private boolean allowPlainHttp;

    String getId() {
        return id;
    }

    String getType() {
        return type;
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getRegistry() {
        return registry;
    }

    String getRepository() {
        return repository;
    }

    String getRegistryServerId() {
        return registryServerId;
    }

    List<File> getCaCertificates() {
        return caCertificates == null ? List.of() : caCertificates;
    }

    boolean isAllowPlainHttp() {
        return allowPlainHttp;
    }
}
