package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static de.eitco.cicd.ociskills.core.oci.SkillStatus.DRAFT;
import static de.eitco.cicd.ociskills.core.oci.SkillStatus.PUBLISHED;
import static de.eitco.cicd.ociskills.maven.Configurations.configure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillRequestsTest {

    private static final SkillId DELTA = new SkillId("cicd", "delta");
    private static final File CA = new File("ca.pem");
    private static final File OWN_CA = new File("own-ca.pem");

    @TempDir
    Path tempDirectory;

    private final List<String> calls = new ArrayList<>();
    private final List<SkillSourceConfiguration> sources = new ArrayList<>();

    private final SkillRequests.Locations locations = new SkillRequests.Locations() {
        @Override
        public SkillSource maven(String sourceId, String groupId, String artifactId, String classifier) {
            calls.add("maven " + sourceId + " " + groupId + " " + artifactId + " '" + classifier + "'");
            return new FakeSkillSource(sourceId, tempDirectory);
        }

        @Override
        public SkillSource oci(String sourceId, String registry, String repository, String registryServerId,
                               boolean allowPlainHttp, List<File> caCertificates) {
            calls.add("oci " + sourceId + " " + registry + " " + repository + " " + registryServerId + " "
                    + allowPlainHttp + " " + caCertificates);
            return new FakeSkillSource(sourceId, tempDirectory);
        }
    };

    /**
     * @param fields pairs of field name and value
     */
    private static SkillConfiguration skill(Object... fields) {
        SkillConfiguration skill = new SkillConfiguration();
        configure(skill, "id", "cicd/delta");
        configure(skill, "version", "1.0.0");
        for (int i = 0; i < fields.length; i += 2) {
            configure(skill, (String) fields[i], fields[i + 1]);
        }
        return skill;
    }

    private static SkillSourceConfiguration oci(String id, String registry, String registryServerId,
                                                boolean allowPlainHttp, List<File> caCertificates) {
        SkillSourceConfiguration source = new SkillSourceConfiguration();
        configure(source, "id", id);
        configure(source, "type", SkillSourceConfiguration.OCI);
        configure(source, "registry", registry);
        configure(source, "registryServerId", registryServerId);
        configure(source, "allowPlainHttp", allowPlainHttp);
        configure(source, "caCertificates", caCertificates);
        return source;
    }

    private SkillRequest request(SkillConfiguration skill) throws MojoFailureException {
        List<SkillRequest> requests = SkillRequests.create(List.of(skill), sources, locations);
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private void assertInvalid(SkillConfiguration skill, String message) {
        assertThatThrownBy(() -> SkillRequests.create(List.of(skill), sources, locations))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageStartingWith("invalid <skill> cicd/delta")
                .hasMessageContaining(message);
        assertThat(calls).isEmpty();
    }

    @Test
    void requestsSkillsFromTheSources() throws Exception {
        SkillRequest request = request(skill("version", " [1.0,2.0) ", "status", "draft", "source", "my-maven",
                "repository", "/teams/acme/skills/"));

        assertThat(request).isEqualTo(new SkillRequest(DELTA, "[1.0,2.0)", DRAFT, "my-maven", "teams/acme/skills"));
        assertThat(calls).isEmpty();
    }

    @Test
    void requestsPublishedSkillsFromAllSourcesByDefault() throws Exception {
        assertThat(request(skill("status", " ", "source", "", "repository", " ")))
                .isEqualTo(new SkillRequest(DELTA, "1.0.0", PUBLISHED, null, null));
    }

    @Test
    void rejectsInvalidRequests() {
        assertThatThrownBy(() -> SkillRequests.create(List.of(skill("id", "delta")), sources, locations))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageStartingWith("invalid <skill> delta");
        assertInvalid(skill("status", "final"), "final");
        assertInvalid(skill("repository", "Teams"), "Teams");
    }

    @Test
    void locatesAMavenArtifactWithTheSkillNameAsClassifier() throws Exception {
        SkillRequest request = request(skill("groupId", "de.eitco.cicd.it", "artifactId", "cicd-skills",
                "status", "draft"));

        assertThat(calls).containsExactly(
                "maven de.eitco.cicd.it:cicd-skills:delta de.eitco.cicd.it cicd-skills 'delta'");
        assertThat(request.location().id()).isEqualTo("de.eitco.cicd.it:cicd-skills:delta");
        assertThat(request.id()).isEqualTo(DELTA);
        assertThat(request.versionSpec()).isEqualTo("1.0.0");
        assertThat(request.minimumStatus()).isEqualTo(DRAFT);
        assertThat(request.source()).isNull();
        assertThat(request.repository()).isNull();
    }

    @Test
    void expandsTheCoordinatesOfAMavenArtifact() throws Exception {
        SkillRequest request = request(skill("id", "acme/tools-delta", "groupId", "com.example.{namespace}",
                "artifactId", "{name[0]}", "classifier", "{name[1:]}"));

        assertThat(calls).containsExactly("maven com.example.acme:tools:delta com.example.acme tools 'delta'");
        assertThat(request.location().id()).isEqualTo("com.example.acme:tools:delta");
    }

    @Test
    void locatesTheMainArtifactWithAnEmptyClassifier() throws Exception {
        SkillRequest request = request(skill("groupId", "de.eitco.cicd.it", "artifactId", "{name}",
                "classifier", "{name[:0]}"));

        assertThat(calls).containsExactly("maven de.eitco.cicd.it:delta de.eitco.cicd.it delta ''");
        assertThat(request.location().id()).isEqualTo("de.eitco.cicd.it:delta");
    }

    @Test
    void rejectsMavenCoordinatesThatCannotBeExpanded() {
        assertInvalid(skill("groupId", "com.example.{repository.parent}", "artifactId", "skills"), "<repository>");
        assertInvalid(skill("groupId", "com.example", "artifactId", "{name[:0]}"), "<artifactId>");
        assertInvalid(skill("groupId", "com.example", "artifactId", "{version}"), "{version}");
    }

    @Test
    void locatesAnImage() throws Exception {
        SkillRequest request = request(skill("image", " registry.example.com:5000/teams/acme//skills/delta/ ",
                "status", "draft"));

        assertThat(calls).containsExactly("oci registry.example.com:5000/teams/acme/skills/delta "
                + "registry.example.com:5000 teams/acme/skills/delta null false []");
        assertThat(request.location().id()).isEqualTo("registry.example.com:5000/teams/acme/skills/delta");
        assertThat(request.minimumStatus()).isEqualTo(DRAFT);
        assertThat(request.source()).isNull();
        assertThat(request.repository()).isNull();
    }

    @Test
    void anImageInheritsTheSettingsOfTheFirstOciSourceOfItsRegistry() throws Exception {
        sources.add(oci("other", "other.example.com", "other", false, List.of()));
        sources.add(oci("first", "https://Registry.example.com/", "first", true, List.of(CA)));
        sources.add(oci("second", "registry.example.com", "second", false, List.of()));

        request(skill("image", "registry.example.com/acme/delta"));

        assertThat(calls).containsExactly("oci registry.example.com/acme/delta registry.example.com acme/delta "
                + "first true [ca.pem]");
    }

    @Test
    void theSettingsOfTheSkillTakePrecedence() throws Exception {
        sources.add(oci("first", "registry.example.com", "first", true, List.of(CA)));

        request(skill("image", "registry.example.com/acme/delta", "registryServerId", "mine",
                "allowPlainHttp", false, "caCertificates", List.of(OWN_CA)));

        assertThat(calls).containsExactly("oci registry.example.com/acme/delta registry.example.com acme/delta "
                + "mine false [own-ca.pem]");
    }

    @Test
    void inheritsTheSettingsNotGivenByTheSkill() throws Exception {
        sources.add(oci("first", "registry.example.com", "first", true, List.of(CA)));

        request(skill("image", "registry.example.com/acme/delta", "registryServerId", "mine"));

        assertThat(calls).containsExactly("oci registry.example.com/acme/delta registry.example.com acme/delta "
                + "mine true [ca.pem]");
    }

    @Test
    void anImageOfAnotherRegistryIsPulledAnonymously() throws Exception {
        sources.add(oci("other", "other.example.com", "other", true, List.of(CA)));

        request(skill("image", "registry.example.com/acme/delta"));

        assertThat(calls).containsExactly("oci registry.example.com/acme/delta registry.example.com acme/delta "
                + "null false []");
    }

    @Test
    void rejectsInvalidImages() {
        assertInvalid(skill("image", "registry.example.com/acme/delta:1.0.0"), "tag");
        assertInvalid(skill("image", "registry.example.com/acme/delta@sha256:" + "a".repeat(64)), "digest");
        assertInvalid(skill("image", "registry.example.com"), "repository");
        assertInvalid(skill("image", "registry.example.com/"), "repository");
        assertInvalid(skill("image", "registry.example.com/Acme/delta"), "Acme");
        assertInvalid(skill("image", "registry example.com/acme/delta"), "registry example.com");
    }

    @Test
    void rejectsContradictingLocations() {
        assertInvalid(skill("image", "registry.example.com/acme/delta", "groupId", "com.example",
                "artifactId", "skills"), "<image>");
        assertInvalid(skill("image", "registry.example.com/acme/delta", "groupId", "com.example"), "<image>");
        assertInvalid(skill("groupId", "com.example"), "<artifactId>");
        assertInvalid(skill("artifactId", "skills"), "<groupId>");
        assertInvalid(skill("classifier", "{name}"), "<classifier>");
        assertInvalid(skill("image", "registry.example.com/acme/delta", "classifier", "{name}"), "<classifier>");
    }

    @Test
    void rejectsRegistrySettingsWithoutImage() {
        assertInvalid(skill("registryServerId", "mine"), "<registryServerId>");
        assertInvalid(skill("allowPlainHttp", true), "<allowPlainHttp>");
        assertInvalid(skill("caCertificates", List.of(CA)), "<caCertificates>");
        assertInvalid(skill("groupId", "com.example", "artifactId", "skills", "registryServerId", "mine"),
                "<registryServerId>");
    }

    @Test
    void rejectsLocationsCombinedWithSourcesOrRepositories() {
        assertInvalid(skill("image", "registry.example.com/acme/delta", "source", "my-registry"), "<source>");
        assertInvalid(skill("image", "registry.example.com/acme/delta", "repository", "teams/acme"),
                "<repository>");
        assertInvalid(skill("groupId", "com.example", "artifactId", "skills", "source", "my-maven"), "<source>");
        assertInvalid(skill("groupId", "com.example", "artifactId", "skills", "repository", "teams/acme"),
                "<repository>");
    }
}
