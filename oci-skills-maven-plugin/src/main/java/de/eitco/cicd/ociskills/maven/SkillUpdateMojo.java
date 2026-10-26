package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.eclipse.aether.RepositorySystem;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Like {@code oci-skills:sync}, but resolves the requested skills again even if they are locked - so the highest
 * version matching the request is installed and locked. Maven sources are checked for new versions regardless of
 * their update policy.
 */
@Mojo(name = "update", threadSafe = true)
public class SkillUpdateMojo extends AbstractSkillSyncMojo {

    /**
     * Comma separated ids ({@code <namespace>/<name>}) or names of the skills to update - all if empty.
     */
    @Parameter(property = "skill")
    private String skill;

    @Inject
    public SkillUpdateMojo(RepositorySystem repositorySystem, SettingsDecrypter settingsDecrypter) {
        super(repositorySystem, settingsDecrypter);
    }

    @Override
    protected boolean checkForUpdates() {
        return true;
    }

    @Override
    protected Predicate<SkillId> update(List<SkillRequest> requests) throws MojoFailureException {
        if (skill == null || skill.isBlank()) {
            return id -> true;
        }
        Set<String> selected = Arrays.stream(skill.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        for (String value : selected) {
            if (requests.stream().noneMatch(request -> matches(request.id(), value))) {
                throw new MojoFailureException("the skill " + value + " is not requested - requested: "
                        + requests.stream().map(request -> request.id().toString())
                        .collect(Collectors.joining(", ")));
            }
        }
        return id -> selected.stream().anyMatch(value -> matches(id, value));
    }

    private static boolean matches(SkillId id, String value) {
        return value.equals(id.toString()) || value.equals(id.name());
    }
}
