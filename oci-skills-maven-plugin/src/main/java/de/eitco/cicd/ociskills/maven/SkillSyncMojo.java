package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.eclipse.aether.RepositorySystem;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Predicate;

/**
 * Installs the requested {@code skills} into {@code installDirectory} - in the versions of the lock file. Requests
 * that are not locked yet or changed are resolved and locked: the highest matching version of the first source
 * knowing the skill is taken. Locked skills may be fetched from any source, but must have the locked digest.
 * Skills of the lock file that are no longer requested are removed. Every installed skill contains a
 * {@code .gitignore} ignoring it in git.
 * <p>
 * The goal is not bound by any lifecycle - add an execution to run it with every build.
 */
@Mojo(name = "sync", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class SkillSyncMojo extends AbstractSkillSyncMojo {

    @Inject
    public SkillSyncMojo(RepositorySystem repositorySystem, SettingsDecrypter settingsDecrypter) {
        super(repositorySystem, settingsDecrypter);
    }

    @Override
    protected Predicate<SkillId> update(List<SkillRequest> requests) {
        return id -> false;
    }
}
