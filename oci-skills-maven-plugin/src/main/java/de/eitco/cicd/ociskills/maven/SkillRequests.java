package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.registry.OciReferenceFactory;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Translates the configured {@code <skills>} into requests. A skill with {@code <image>} or {@code <groupId>} and
 * {@code <artifactId>} is located directly - it gets a source of its own, whose id is
 * {@code <groupId>:<artifactId>[:<classifier>]} or {@code <registry>/<repository>}.
 */
final class SkillRequests {

    /**
     * Creates the sources of skills located directly by their {@code <skill>}.
     */
    interface Locations {

        /**
         * @param classifier the expanded classifier - empty for the main artifact
         */
        SkillSource maven(String sourceId, String groupId, String artifactId, String classifier);

        SkillSource oci(String sourceId, String registry, String repository, String registryServerId,
                        boolean allowPlainHttp, List<File> caCertificates) throws MojoFailureException;
    }

    private static final Pattern REGISTRY = Pattern.compile("[A-Za-z0-9.-]+(:[0-9]+)?");
    private static final String MAVEN_LOCATION = "<groupId>/<artifactId>";

    private SkillRequests() {
    }

    /**
     * @param sources the configured sources - an {@code <image>} inherits credentials and TLS settings of the first
     *                {@code oci} source with the same registry
     * @throws MojoFailureException if a skill is configured incorrectly
     */
    static List<SkillRequest> create(List<SkillConfiguration> skills, List<SkillSourceConfiguration> sources,
                                     Locations locations) throws MojoFailureException {
        List<SkillRequest> requests = new ArrayList<>();
        for (SkillConfiguration skill : skills) {
            try {
                requests.add(request(skill, sources, locations));
            } catch (IllegalArgumentException e) {
                throw new MojoFailureException("invalid <skill> " + skill.getId() + ": " + e.getMessage(), e);
            }
        }
        return requests;
    }

    private static SkillRequest request(SkillConfiguration skill, List<SkillSourceConfiguration> sources,
                                        Locations locations) throws MojoFailureException {
        SkillId id = SkillId.parse(skill.getId());
        String version = skill.getVersion();
        SkillVersions.toRange(version);
        SkillStatus status = value(skill.getStatus()) == null ? SkillStatus.PUBLISHED
                : SkillStatus.parse(skill.getStatus());
        String source = value(skill.getSource());
        String repository = value(skill.getRepository()) == null ? null
                : OciReferenceFactory.normalizeRepository(skill.getRepository());
        String image = value(skill.getImage());
        String groupId = value(skill.getGroupId());
        String artifactId = value(skill.getArtifactId());
        String classifier = value(skill.getClassifier());
        checkLocation(skill, source, repository, image, groupId, artifactId, classifier);
        if (image != null) {
            return SkillRequest.located(id, version.trim(), status, image(skill, image, sources, locations));
        }
        if (groupId != null) {
            return SkillRequest.located(id, version.trim(), status,
                    maven(id, groupId, artifactId, classifier, locations));
        }
        return new SkillRequest(id, version.trim(), status, source, repository);
    }

    private static void checkLocation(SkillConfiguration skill, String source, String repository, String image,
                                      String groupId, String artifactId, String classifier) {
        boolean maven = groupId != null || artifactId != null;
        if (image != null && maven) {
            throw new IllegalArgumentException("<image> and " + MAVEN_LOCATION + " exclude each other");
        }
        if (maven && (groupId == null || artifactId == null)) {
            throw new IllegalArgumentException("<groupId> and <artifactId> must be configured together");
        }
        if (classifier != null && !maven) {
            throw new IllegalArgumentException("<classifier> needs <groupId> and <artifactId>");
        }
        if (image == null) {
            if (value(skill.getRegistryServerId()) != null) {
                throw new IllegalArgumentException("<registryServerId> needs <image>");
            }
            if (skill.getAllowPlainHttp() != null) {
                throw new IllegalArgumentException("<allowPlainHttp> needs <image>");
            }
            if (!skill.getCaCertificates().isEmpty()) {
                throw new IllegalArgumentException("<caCertificates> needs <image>");
            }
        }
        if (image != null || maven) {
            String location = image != null ? "<image>" : MAVEN_LOCATION;
            if (source != null) {
                throw new IllegalArgumentException("<source> cannot be combined with " + location
                        + " - the skill is pulled from there only");
            }
            if (repository != null) {
                throw new IllegalArgumentException("<repository> cannot be combined with " + location
                        + " - the location of the skill is given completely");
            }
        }
    }

    private static SkillSource maven(SkillId id, String groupId, String artifactId, String classifier,
                                     Locations locations) {
        String expandedGroupId = coordinate("groupId", groupId, id);
        String expandedArtifactId = coordinate("artifactId", artifactId, id);
        String expandedClassifier = new SourceTemplate("classifier", classifier == null ? "{name}" : classifier)
                .expand(id, null);
        String sourceId = expandedGroupId + ":" + expandedArtifactId
                + (expandedClassifier.isEmpty() ? "" : ":" + expandedClassifier);
        return locations.maven(sourceId, expandedGroupId, expandedArtifactId, expandedClassifier);
    }

    private static String coordinate(String parameter, String template, SkillId id) {
        String value = new SourceTemplate(parameter, template).expand(id, null);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("<" + parameter + "> " + template + " is empty for " + id);
        }
        return value;
    }

    private static SkillSource image(SkillConfiguration skill, String image, List<SkillSourceConfiguration> sources,
                                     Locations locations) throws MojoFailureException {
        if (image.contains("@")) {
            throw new IllegalArgumentException("<image> " + image + " must not contain a digest - the tag is "
                    + "<version>[-<status>]");
        }
        int slash = image.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("<image> " + image + " has no repository - expected "
                    + "<registry>/<repository>");
        }
        String registry = image.substring(0, slash);
        if (!REGISTRY.matcher(registry).matches()) {
            throw new IllegalArgumentException("<image> " + image + " has the invalid registry '" + registry
                    + "' - expected host[:port]");
        }
        String path = image.substring(slash + 1);
        if (path.contains(":")) {
            throw new IllegalArgumentException("<image> " + image + " must not contain a tag - the tag is "
                    + "<version>[-<status>]");
        }
        String repository = OciReferenceFactory.normalizeRepository(path);
        SkillSourceConfiguration inherited = sources.stream()
                .filter(source -> SkillSourceConfiguration.OCI.equals(value(source.getType())))
                .filter(source -> host(source.getRegistry()).equals(host(registry)))
                .findFirst()
                .orElse(null);
        String registryServerId = value(skill.getRegistryServerId()) != null ? value(skill.getRegistryServerId())
                : inherited == null ? null : value(inherited.getRegistryServerId());
        boolean allowPlainHttp = skill.getAllowPlainHttp() != null ? skill.getAllowPlainHttp()
                : inherited != null && inherited.isAllowPlainHttp();
        List<File> caCertificates = !skill.getCaCertificates().isEmpty() ? skill.getCaCertificates()
                : inherited == null ? List.of() : inherited.getCaCertificates();
        return locations.oci(registry + "/" + repository, registry, repository, registryServerId, allowPlainHttp,
                caCertificates);
    }

    /**
     * @return the registry host comparable to others - without scheme and in lower case
     */
    private static String host(String registry) {
        return registry == null ? "" : registry.trim().replaceFirst("^[a-zA-Z]+://", "").replaceAll("/+$", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * @return the trimmed value - {@code null} if it is blank
     */
    private static String value(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
