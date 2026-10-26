package de.eitco.cicd.ociskills.maven;

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

    @Test
    void takesTheVersionsFromTheTags() throws Exception {
        tags = List.of("1.0.0", "1.1.0-draft", "1.1.0", "1.2.0-testing", "2.0.0", "latest-unrelated");

        assertThat(source().versions(NEXUS_TEST, "[1.0,2.0)")).containsExactlyInAnyOrder("1.0.0", "1.1.0", "1.2.0");
        assertThat(calls).containsExactly("tags nexus.eitco.de/docker/cicd/nexus-test reader");
    }

    @Test
    void pullsTheTagOfVersionAndStatus() throws Exception {
        source().fetch(NEXUS_TEST, "1.1.0", DRAFT, DIGEST, tempDirectory);
        source().fetch(NEXUS_TEST, "1.1.0", PUBLISHED, null, tempDirectory);

        assertThat(calls).containsExactly(
                "pull nexus.eitco.de/docker/cicd/nexus-test:1.1.0-draft " + DIGEST + " reader",
                "pull nexus.eitco.de/docker/cicd/nexus-test:1.1.0 null reader");
    }
}
