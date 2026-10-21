package de.eitco.cicd.ociskills.core.registry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryCredentialsTest {

    @Test
    void neverRevealsPasswordInToString() {
        assertThat(new RegistryCredentials("user", "s3cr3t").toString())
                .contains("user")
                .doesNotContain("s3cr3t");
    }

    @Test
    void neverRevealsTrustStorePasswordInToString() {
        assertThat(new RegistryConnection(null, java.nio.file.Path.of("trust.p12"), "s3cr3t", "PKCS12", false)
                .toString()).doesNotContain("s3cr3t");
    }

    @Test
    void anonymousCredentials() {
        assertThat(RegistryCredentials.anonymous().isAnonymous()).isTrue();
        assertThat(new RegistryCredentials("user", "pw").isAnonymous()).isFalse();
    }
}
