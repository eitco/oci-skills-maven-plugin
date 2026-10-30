package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.OciRegistryClient;
import de.eitco.cicd.ociskills.core.registry.PushResult;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.eitco.cicd.ociskills.core.oci.SkillStatus.DRAFT;
import static de.eitco.cicd.ociskills.core.oci.SkillStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OciSkillSourceTest {

    private static final SkillId NEXUS_TEST = new SkillId("cicd", "nexus-test");
    private static final RegistryCredentials CREDENTIALS = new RegistryCredentials("reader", "s3cr3t");
    private static final Digest DIGEST = Digest.parse("sha256:" + "a".repeat(64));

    @TempDir
    Path tempDirectory;

    private final List<String> calls = new ArrayList<>();
    private List<String> tags = List.of();

    private final OciRegistryClient client = new OciRegistryClient() {
        @Override
        public PushResult push(OciImageLayout image, OciReference destination, RegistryCredentials credentials) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> tags(OciReference repository, RegistryCredentials credentials) {
            calls.add("tags " + repository.registry() + "/" + repository.repository() + " "
                    + credentials.username());
            return tags;
        }

        @Override
        public Optional<OciImageLayout> pull(OciReference source, Digest digest, Path directory,
                                             RegistryCredentials credentials) {
            calls.add("pull " + source + " " + digest + " " + credentials.username());
            return Optional.empty();
        }
    };

    private OciSkillSource source() {
        return new OciSkillSource("oci", "nexus.eitco.de", new SourceTemplate("repository", "docker/{namespace}"),
                client, CREDENTIALS);
    }

    private OciSkillSource sourceWithoutRepository() {
        return new OciSkillSource("oci", "nexus.eitco.de", null, client, CREDENTIALS);
    }

    @Test
    void takesTheVersionsFromTheTags() throws Exception {
        tags = List.of("1.0.0", "1.1.0-draft", "1.1.0", "1.2.0-testing", "2.0.0", "latest-unrelated");

        assertThat(source().versions(NEXUS_TEST, null, "[1.0,2.0)"))
                .containsExactlyInAnyOrder("1.0.0", "1.1.0", "1.2.0");
        assertThat(calls).containsExactly("tags nexus.eitco.de/docker/cicd/nexus-test reader");
    }

    @Test
    void pullsTheTagOfVersionAndStatus() throws Exception {
        source().fetch(NEXUS_TEST, null, "1.1.0", DRAFT, DIGEST, tempDirectory);
        source().fetch(NEXUS_TEST, null, "1.1.0", PUBLISHED, null, tempDirectory);

        assertThat(calls).containsExactly(
                "pull nexus.eitco.de/docker/cicd/nexus-test:1.1.0-draft " + DIGEST + " reader",
                "pull nexus.eitco.de/docker/cicd/nexus-test:1.1.0 null reader");
    }

    @Test
    void prefersTheRepositoryOfTheSkill() throws Exception {
        source().versions(NEXUS_TEST, "teams/acme/skills", "[1.0,2.0)");
        sourceWithoutRepository().fetch(NEXUS_TEST, "teams/acme/skills", "1.1.0", PUBLISHED, null, tempDirectory);

        assertThat(calls).containsExactly("tags nexus.eitco.de/teams/acme/skills/nexus-test reader",
                "pull nexus.eitco.de/teams/acme/skills/nexus-test:1.1.0 null reader");
    }

    @Test
    void pullsAFixedImage() throws Exception {
        tags = List.of("1.0.0", "1.1.0-draft");
        OciSkillSource image = OciSkillSource.image("nexus.eitco.de/teams/acme/nexus-test", "nexus.eitco.de",
                "teams/acme/nexus-test", client, CREDENTIALS);

        assertThat(image.id()).isEqualTo("nexus.eitco.de/teams/acme/nexus-test");
        assertThat(image.versions(NEXUS_TEST, null, "[1.0,2.0)")).containsExactlyInAnyOrder("1.0.0", "1.1.0");
        image.fetch(NEXUS_TEST, null, "1.1.0", DRAFT, DIGEST, tempDirectory);

        assertThat(calls).containsExactly("tags nexus.eitco.de/teams/acme/nexus-test reader",
                "pull nexus.eitco.de/teams/acme/nexus-test:1.1.0-draft " + DIGEST + " reader");
    }

    @Test
    void needsARepository() {
        assertThatThrownBy(() -> sourceWithoutRepository().versions(NEXUS_TEST, null, "[1.0,2.0)"))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("oci")
                .hasMessageContaining("<repository>");
        assertThatThrownBy(() -> sourceWithoutRepository().fetch(NEXUS_TEST, null, "1.1.0", PUBLISHED, null,
                tempDirectory))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("<repository>");
        assertThat(calls).isEmpty();
    }
}
