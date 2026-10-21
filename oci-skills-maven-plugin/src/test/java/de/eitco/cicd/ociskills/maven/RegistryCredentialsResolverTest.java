package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsProblem;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryCredentialsResolverTest {

    /**
     * Decrypts by stripping the braces of {@code {secret}}, like a very simple master password.
     */
    private static final SettingsDecrypter DECRYPTER = request -> result(request, null);

    private static SettingsDecryptionResult result(SettingsDecryptionRequest request, SettingsProblem problem) {
        List<Server> servers = request.getServers().stream().map(server -> {
            Server decrypted = server.clone();
            String password = server.getPassword();
            if (password != null && password.startsWith("{") && password.endsWith("}")) {
                decrypted.setPassword(password.substring(1, password.length() - 1));
            }
            return decrypted;
        }).toList();
        return new SettingsDecryptionResult() {
            @Override
            public org.apache.maven.settings.Server getServer() {
                return servers.isEmpty() ? null : servers.get(0);
            }

            @Override
            public List<Server> getServers() {
                return servers;
            }

            @Override
            public org.apache.maven.settings.Proxy getProxy() {
                return null;
            }

            @Override
            public List<org.apache.maven.settings.Proxy> getProxies() {
                return List.of();
            }

            @Override
            public List<SettingsProblem> getProblems() {
                return problem == null ? List.of() : List.of(problem);
            }
        };
    }

    private static Settings settingsWith(String id, String username, String password) {
        Server server = new Server();
        server.setId(id);
        server.setUsername(username);
        server.setPassword(password);
        Settings settings = new Settings();
        settings.addServer(server);
        return settings;
    }

    @Test
    void resolvesCredentialsOfServer() throws Exception {
        RegistryCredentials credentials = new RegistryCredentialsResolver(DECRYPTER)
                .resolve(settingsWith("eitco-oci", "deployer", "plain"), "eitco-oci");

        assertThat(credentials).isEqualTo(new RegistryCredentials("deployer", "plain"));
    }

    @Test
    void decryptsEncryptedPasswords() throws Exception {
        RegistryCredentials credentials = new RegistryCredentialsResolver(DECRYPTER)
                .resolve(settingsWith("eitco-oci", "deployer", "{s3cr3t}"), "eitco-oci");

        assertThat(credentials.password()).isEqualTo("s3cr3t");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isAnonymousWithoutServerId(String serverId) throws Exception {
        assertThat(new RegistryCredentialsResolver(DECRYPTER).resolve(new Settings(), serverId).isAnonymous())
                .isTrue();
    }

    @Test
    void failsIfServerDoesNotExist() {
        assertThatThrownBy(() -> new RegistryCredentialsResolver(DECRYPTER)
                .resolve(settingsWith("other", "deployer", "s3cr3t"), "eitco-oci"))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("eitco-oci")
                .hasMessageContaining("settings.xml");
    }

    @Test
    void failsOnDecryptionProblemsWithoutRevealingPassword() {
        SettingsDecrypter failing = request -> result(request,
                new DefaultSettingsProblem("cannot decrypt", SettingsProblem.Severity.ERROR, "settings", -1, -1,
                        null));

        assertThatThrownBy(() -> new RegistryCredentialsResolver(failing)
                .resolve(settingsWith("eitco-oci", "deployer", "{s3cr3t}"), "eitco-oci"))
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("eitco-oci")
                .hasMessageContaining("cannot decrypt")
                .hasMessageNotContaining("s3cr3t");
    }
}
