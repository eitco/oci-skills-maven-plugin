package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import java.util.stream.Collectors;

/**
 * Resolves registry credentials from the {@code <servers>} of the maven settings - including encrypted passwords.
 */
final class RegistryCredentialsResolver {

    private final SettingsDecrypter settingsDecrypter;

    RegistryCredentialsResolver(SettingsDecrypter settingsDecrypter) {
        this.settingsDecrypter = settingsDecrypter;
    }

    /**
     * @param settings the maven settings
     * @param serverId the id of the server entry - {@code null} or empty for anonymous access
     * @throws MojoFailureException if the server entry does not exist or cannot be decrypted
     */
    RegistryCredentials resolve(Settings settings, String serverId) throws MojoFailureException {
        if (serverId == null || serverId.isBlank()) {
            return RegistryCredentials.anonymous();
        }
        Server server = settings.getServer(serverId);
        if (server == null) {
            throw new MojoFailureException("no <server> with id '" + serverId + "' found in settings.xml - "
                    + "configure the registry credentials there");
        }
        SettingsDecryptionResult result = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
        if (result.getProblems().stream().anyMatch(problem -> problem.getSeverity() != SettingsProblem.Severity.WARNING)) {
            throw new MojoFailureException("credentials of server '" + serverId + "' cannot be decrypted: "
                    + result.getProblems().stream().map(SettingsProblem::getMessage).collect(Collectors.joining("; ")));
        }
        Server decrypted = result.getServer();
        return new RegistryCredentials(decrypted.getUsername(), decrypted.getPassword());
    }
}
