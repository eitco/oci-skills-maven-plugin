package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.PushResult;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OrasRegistryClientTlsTest {

    private static final String STORE_PASSWORD = "changeit";

    @TempDir
    static Path certificates;

    private static Path serverKeyStore;
    private static Path caPem;
    private static Path trustStore;

    @TempDir
    Path tempDirectory;

    private FakeOciRegistry registry;
    private OciImageLayout image;

    @BeforeAll
    static void createCertificates() throws Exception {
        serverKeyStore = certificates.resolve("server.p12");
        caPem = certificates.resolve("ca.pem");
        trustStore = certificates.resolve("trust.p12");
        keytool("-genkeypair", "-alias", "registry", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", serverKeyStore.toString(), "-storetype", "PKCS12", "-storepass", STORE_PASSWORD);
        keytool("-exportcert", "-rfc", "-alias", "registry", "-keystore", serverKeyStore.toString(),
                "-storepass", STORE_PASSWORD, "-file", caPem.toString());
        keytool("-importcert", "-noprompt", "-alias", "registry", "-file", caPem.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", STORE_PASSWORD);
    }

    static void keytool(String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }

    @BeforeEach
    void setUp() throws Exception {
        registry = FakeOciRegistry.https(serverKeyStore, STORE_PASSWORD);
        image = OrasRegistryClientTest.buildImage(tempDirectory);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    private OciReference destination() {
        return new OciReference(registry.address(), "skills/nexus-test", "0.0.1-draft");
    }

    @Test
    void rejectsUntrustedCertificate() {
        SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                () -> new OrasRegistryClient(RegistryConnection.defaults())
                        .push(image, destination(), RegistryCredentials.anonymous()));

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.TLS_UNTRUSTED);
        assertThat(registry.blobs()).isEmpty();
    }

    @Test
    void trustsConfiguredPemCertificate() throws Exception {
        RegistryConnection connection = new RegistryConnection(List.of(caPem), null, null, null, false);

        PushResult result = new OrasRegistryClient(connection).push(image, destination(),
                RegistryCredentials.anonymous());

        assertThat(result.confirmedDigest()).isEqualTo(image.manifestDescriptor().digest());
    }

    @Test
    void trustsConfiguredTrustStore() throws Exception {
        RegistryConnection connection = new RegistryConnection(List.of(), trustStore, STORE_PASSWORD, "PKCS12", false);

        PushResult result = new OrasRegistryClient(connection).push(image, destination(),
                RegistryCredentials.anonymous());

        assertThat(result.confirmedDigest()).isEqualTo(image.manifestDescriptor().digest());
    }

    @Test
    void failsOnMissingPemFile() {
        RegistryConnection connection = new RegistryConnection(List.of(certificates.resolve("missing.pem")), null,
                null, null, false);

        SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                () -> new OrasRegistryClient(connection).push(image, destination(), RegistryCredentials.anonymous()));

        assertThat(exception).hasMessageContaining("missing.pem");
    }

    @Test
    void failsOnWrongTrustStorePasswordWithoutRevealingIt() {
        RegistryConnection connection = new RegistryConnection(List.of(), trustStore, "wrong-p4ss", "PKCS12", false);

        SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                () -> new OrasRegistryClient(connection).push(image, destination(), RegistryCredentials.anonymous()));

        assertThat(exception).hasMessageContaining("trust.p12");
        assertThat(OrasRegistryClientTest.fullMessages(exception)).doesNotContain("wrong-p4ss");
    }

    @Test
    void stillTrustsDefaultCertificateAuthoritiesWhenCustomCaIsConfigured() throws Exception {
        RegistryConnection connection = new RegistryConnection(List.of(caPem), null, null, null, false);

        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        java.util.List<java.security.cert.Certificate> bundle = java.util.List.copyOf(factory.generateCertificates(
                new java.io.ByteArrayInputStream(TrustMaterial.pemBundle(connection).getBytes())));
        java.security.cert.Certificate ca;
        try (var in = Files.newInputStream(caPem)) {
            ca = factory.generateCertificate(in);
        }

        assertThat(bundle).contains(ca);
        assertThat(bundle.size()).isGreaterThan(10);
    }
}
