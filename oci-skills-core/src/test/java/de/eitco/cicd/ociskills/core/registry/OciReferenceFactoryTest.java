package de.eitco.cicd.ociskills.core.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OciReferenceFactoryTest {

    private final OciReferenceFactory factory = new OciReferenceFactory();

    @Test
    void createsReferenceFromConceptExample() {
        OciReference reference = factory.create("nexus.eitco.de", "docker-cicd-prerelease/eitco/cicd", "nexus-test",
                "0.0.1-draft");

        assertThat(reference).hasToString("nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test:0.0.1-draft");
        assertThat(reference.registry()).isEqualTo("nexus.eitco.de");
        assertThat(reference.repository()).isEqualTo("docker-cicd-prerelease/eitco/cicd/nexus-test");
        assertThat(reference.tag()).isEqualTo("0.0.1-draft");
    }

    @ParameterizedTest
    @CsvSource({
            "/docker-cicd/eitco/,  docker-cicd/eitco/nexus-test",
            "docker-cicd//eitco,   docker-cicd/eitco/nexus-test",
            "'',                   nexus-test",
            "/,                    nexus-test",
    })
    void normalizesSlashesInRepository(String repository, String expected) {
        assertThat(factory.create("localhost:5000", repository, "nexus-test", "0.0.1").repository())
                .isEqualTo(expected);
    }

    @Test
    void keepsPortAndStripsSchemeAndTrailingSlashOfRegistry() {
        assertThat(factory.create("https://localhost:5000/", "skills", "nexus-test", "0.0.1"))
                .hasToString("localhost:5000/skills/nexus-test:0.0.1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingRegistry(String registry) {
        assertThatThrownBy(() -> factory.create(registry, "skills", "nexus-test", "0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registry");
    }

    @Test
    void rejectsMissingRepository() {
        assertThatThrownBy(() -> factory.create("localhost:5000", null, "nexus-test", "0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository");
    }

    @ParameterizedTest
    @CsvSource({"Skills", "sk ills", "skills/../x", "skills:latest"})
    void rejectsInvalidRepositories(String repository) {
        assertThatThrownBy(() -> factory.create("localhost:5000", repository, "nexus-test", "0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository");
        assertThatThrownBy(() -> OciReferenceFactory.normalizeRepository(repository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(repository);
    }

    @ParameterizedTest
    @CsvSource({
            "teams/acme/skills,     teams/acme/skills",
            "/teams//acme/skills/,  teams/acme/skills",
            "' skills ',            skills",
    })
    void normalizesRepositories(String repository, String expected) {
        assertThat(OciReferenceFactory.normalizeRepository(repository)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @CsvSource({"' '", "/"})
    void rejectsEmptyRepositories(String repository) {
        assertThatThrownBy(() -> OciReferenceFactory.normalizeRepository(repository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository");
    }
}
