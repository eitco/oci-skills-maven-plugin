package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillBuildRequest;
import de.eitco.cicd.ociskills.core.SkillImageBuilder;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pulls images pushed by the plugin with <a href="https://github.com/redhat-et/skillimage">skillctl</a> in the version
 * pinned by {@link SkillctlDownload} - downloaded before the first test. Skipped if the download fails, e.g. without
 * internet access, or the platform is not supported. {@code -Dskillctl.executable=<path>} uses another skillctl.
 */
class SkillctlCompatibilityTest {

    private static final String STORE_PASSWORD = "changeit";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path directory;

    private Path skillctl;
    private Path caPem;
    private FakeOciRegistry registry;
    private OciImageLayout image;

    @BeforeEach
    void setUp() throws Exception {
        skillctl = SkillctlDownload.executable();
        // skillctl talks TLS to every registry - a plain http registry is not supported
        Path serverKeyStore = directory.resolve("server.p12");
        caPem = directory.resolve("ca.pem");
        OrasRegistryClientTlsTest.keytool("-genkeypair", "-alias", "registry", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", serverKeyStore.toString(), "-storetype", "PKCS12", "-storepass", STORE_PASSWORD);
        OrasRegistryClientTlsTest.keytool("-exportcert", "-rfc", "-alias", "registry",
                "-keystore", serverKeyStore.toString(), "-storepass", STORE_PASSWORD, "-file", caPem.toString());
        registry = FakeOciRegistry.https(serverKeyStore, STORE_PASSWORD);
        image = buildImage();
        new OrasRegistryClient(new RegistryConnection(List.of(caPem), null, null, null, false))
                .push(image, reference(), RegistryCredentials.anonymous());
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.close();
        }
    }

    @Test
    void pullsAndUnpacksThePushedImage() throws Exception {
        Path output = Files.createDirectories(directory.resolve("pulled"));

        ExternalTool.Result result = skillctl("pull", reference().toString(), "-o", output.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        Path skill = output.resolve("nexus-test");
        Path extracted = directory.resolve("extracted");
        image.extract(extracted);
        assertThat(files(skill)).isEqualTo(files(extracted));
        assertThat(files(skill)).containsKeys("SKILL.md", "skill.yaml", "scripts/run.sh");
        // no check of the executable bits: skillctl 0.7 unpacks every file without them - even its own images
    }

    @Test
    void readsTheSkillCardOfThePulledImage() throws Exception {
        assertThat(skillctl("pull", reference().toString()).exitCode()).isZero();

        ExternalTool.Result result = skillctl("inspect", reference().toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("nexus-test").contains("1.2.0").contains("published")
                .contains("Test skill for skillctl").contains(image.manifestDescriptor().digest().toString());
    }

    private ExternalTool.Result skillctl(String... arguments) throws IOException, InterruptedException {
        // skillctl keeps a local store below the home directory - keep it out of the real one
        Path home = Files.createDirectories(directory.resolve("home"));
        Map<String, String> environment = Map.of("HOME", home.toString(), "SSL_CERT_FILE", caPem.toString(),
                "XDG_DATA_HOME", home.resolve(".local/share").toString(),
                "XDG_CONFIG_HOME", home.resolve(".config").toString(),
                "XDG_CACHE_HOME", home.resolve(".cache").toString());
        List<String> command = Stream.concat(Stream.of(skillctl.toString()), Stream.of(arguments)).toList();
        try {
            return ExternalTool.run(directory, environment, TIMEOUT, command);
        } catch (IOException e) {
            throw new IOException(e.getMessage() + "\nrequests to the registry: " + registry.requests(), e);
        }
    }

    private OciReference reference() {
        return new OciReference(registry.address(), "skills/nexus-test", "1.2.0");
    }

    private OciImageLayout buildImage() throws Exception {
        Path skill = Files.createDirectories(directory.resolve("src/nexus-test/scripts")).getParent();
        Files.writeString(skill.resolve("SKILL.md"), """
                ---
                name: nexus-test
                description: Test skill for skillctl
                ---

                # Nexus Test

                Run `scripts/run.sh`.
                """);
        Files.writeString(skill.resolve("scripts/run.sh"), "#!/bin/sh\necho hello\n");
        Path layout = directory.resolve("skill-oci");
        new SkillImageBuilder().build(new SkillBuildRequest(skill, layout,
                new FileSelection(null, null, List.of("scripts/**")), SkillStatus.PUBLISHED,
                Instant.parse("2026-10-16T14:45:03Z"), new SkillCardSettings("cicd", "1.2.0"),
                directory.resolve("generated/nexus-test")));
        return new OciLayoutReader().read(layout);
    }

    private static Map<String, String> files(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        }
        return files;
    }
}
