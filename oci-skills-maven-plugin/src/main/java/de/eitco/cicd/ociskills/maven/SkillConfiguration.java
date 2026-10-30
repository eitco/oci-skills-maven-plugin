package de.eitco.cicd.ociskills.maven;

import java.io.File;
import java.util.List;

/**
 * The configuration of a requested {@code <skill>}. By default it is resolved from the configured sources - with
 * {@code <image>} or {@code <groupId>} and {@code <artifactId>} it is located directly, without source.
 */
public class SkillConfiguration {

    /**
     * The skill: {@code <namespace>/<name>} - the namespace and name of its SkillCard.
     */
    private String id;

    /**
     * A version range like {@code [1.0,2.0)} or an exact version like {@code 1.2.0}. The highest version in the
     * range is installed and locked until it is updated with {@code oci-skills:update}.
     */
    private String version;

    /**
     * The least mature status accepted: {@code published} (default), {@code testing} or {@code draft}. Of a version
     * with several statuses the most mature is taken.
     */
    private String status;

    /**
     * The id of the only source to resolve the skill from - by default all sources are asked in their order.
     */
    private String source;

    /**
     * The OCI repository path the skill is stored below, e.g. {@code teams/acme/skills} - the {@code <repository>}
     * it was pushed with. OCI sources pull {@code <registry>/<repository>/<name>}, maven sources derive group and
     * artifact id from it with the placeholders {@code {repository}}, {@code {repository.parent}} and
     * {@code {repository.last}}. Without it, the sources use their own configuration.
     */
    private String repository;

    /**
     * The OCI repository of the skill without tag, e.g. {@code registry.example.com/teams/acme/skills/nexus-test} -
     * the tag is the version suffixed with the status unless the status is {@code published}. The skill is pulled
     * from there only, the sources are not asked. Excludes {@code <groupId>}/{@code <artifactId>}, {@code <source>}
     * and {@code <repository>}.
     */
    private String image;

    /**
     * With {@code <image>}: the id of the {@code <server>} in the maven settings holding the registry credentials -
     * by default the one of the first {@code oci} source with the same registry, otherwise the registry is accessed
     * anonymously.
     */
    private String registryServerId;

    /**
     * With {@code <image>}: whether to connect to the registry via plain http instead of https - by default the
     * setting of the first {@code oci} source with the same registry, otherwise {@code false}. Only meant for local
     * test registries.
     */
    private Boolean allowPlainHttp;

    /**
     * With {@code <image>}: PEM files of additional certificate authorities to trust for the registry connection -
     * by default the ones of the first {@code oci} source with the same registry.
     */
    private List<File> caCertificates;

    /**
     * The group id of the maven artifact containing the skill - together with {@code <artifactId>}. The skill is
     * pulled from there only, the sources are not asked. May contain the placeholders of the skill id, see
     * {@link SourceTemplate}. Excludes {@code <image>}, {@code <source>} and {@code <repository>}.
     */
    private String groupId;

    /**
     * The artifact id of the maven artifact containing the skill - together with {@code <groupId>}.
     */
    private String artifactId;

    /**
     * With {@code <groupId>} and {@code <artifactId>}: the classifier of the artifact, suffixed with the status
     * unless the status is {@code published} - defaults to {@code {name}}, like {@code oci-skills:archive} attaches
     * skills. If it expands to an empty value, e.g. {@code {name[:0]}}, the main artifact is used.
     */
    private String classifier;

    String getId() {
        return id;
    }

    String getVersion() {
        return version;
    }

    String getStatus() {
        return status;
    }

    String getSource() {
        return source;
    }

    String getRepository() {
        return repository;
    }

    String getImage() {
        return image;
    }

    String getRegistryServerId() {
        return registryServerId;
    }

    /**
     * @return {@code null} if not configured
     */
    Boolean getAllowPlainHttp() {
        return allowPlainHttp;
    }

    List<File> getCaCertificates() {
        return caCertificates == null ? List.of() : caCertificates;
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getClassifier() {
        return classifier;
    }

    @Override
    public String toString() {
        return "SkillConfiguration[id=" + id + ", version=" + version + ", status=" + status + ", source=" + source
                + ", repository=" + repository + ", image=" + image + ", registryServerId=" + registryServerId
                + ", allowPlainHttp=" + allowPlainHttp + ", caCertificates=" + caCertificates + ", groupId=" + groupId
                + ", artifactId=" + artifactId + ", classifier=" + classifier + "]";
    }
}
