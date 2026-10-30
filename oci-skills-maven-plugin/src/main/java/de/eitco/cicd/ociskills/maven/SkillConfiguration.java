package de.eitco.cicd.ociskills.maven;

/**
 * The configuration of a requested {@code <skill>}.
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
}
