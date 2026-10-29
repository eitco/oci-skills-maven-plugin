package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.LockedSkill;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillInstaller;
import de.eitco.cicd.ociskills.core.install.SkillLockFile;
import de.eitco.cicd.ociskills.core.install.SkillOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Brings the installed skills in line with the requested skills and the lock file:
 * <ol>
 *     <li>requests without lock entry, with a changed version or status, or selected for update are resolved -
 *     all others keep their locked version and digest</li>
 *     <li>skills whose installed digest differs from the locked digest are fetched and installed</li>
 *     <li>installed skills of the lock file that are no longer requested are removed - skills installed
 *     without lock file, e.g. by {@code oci-skills:pull}, are kept</li>
 *     <li>the lock file is written</li>
 * </ol>
 * The work directory is deleted afterwards.
 */
final class SkillSynchronizer {

    private final SkillResolver resolver;
    private final SkillInstaller installer;
    private final SkillLockFile lockFile;
    private final Path workDirectory;
    private final Consumer<String> log;

    SkillSynchronizer(SkillResolver resolver, SkillInstaller installer, SkillLockFile lockFile, Path workDirectory,
                      Consumer<String> log) {
        this.resolver = resolver;
        this.installer = installer;
        this.lockFile = lockFile;
        this.workDirectory = workDirectory;
        this.log = log;
    }

    /**
     * @param update selects the skills to resolve again even if they are locked
     */
    void sync(List<SkillRequest> requests, Predicate<SkillId> update) throws SkillPullException {
        checkNames(requests);
        Map<SkillId, LockedSkill> locked = new HashMap<>();
        for (LockedSkill skill : lockFile.read()) {
            locked.put(skill.id(), skill);
        }
        Map<String, SkillOrigin> installed = installer.installed();
        try {
            List<LockedSkill> lock = new ArrayList<>();
            for (SkillRequest request : requests) {
                lock.add(sync(request, locked.get(request.id()), installed.get(request.id().name()), update));
            }
            Set<SkillId> requested = requests.stream().map(SkillRequest::id).collect(Collectors.toSet());
            for (SkillId id : locked.keySet()) {
                SkillOrigin origin = installed.get(id.name());
                if (!requested.contains(id) && origin != null && origin.id().equals(id)) {
                    installer.remove(id.name());
                    log.accept("Removed " + id + " - it is no longer requested");
                }
            }
            lockFile.write(lock);
        } finally {
            delete(workDirectory);
        }
    }

    private LockedSkill sync(SkillRequest request, LockedSkill locked, SkillOrigin installed,
                             Predicate<SkillId> update) throws SkillPullException {
        if (locked == null || update.test(request.id()) || !matches(request, locked)) {
            SkillResolver.Resolved resolved = resolver.resolve(request.id(), request.versionSpec(),
                    request.minimumStatus(), request.source(), workDirectory);
            if (isInstalled(installed, resolved.origin())) {
                log.accept(describe(resolved.origin()) + " is up to date");
            } else {
                installer.install(resolved.image(), resolved.origin());
                log.accept("Installed " + describe(resolved.origin()));
            }
            return new LockedSkill(request.versionSpec(), request.minimumStatus(), resolved.origin());
        }
        if (isInstalled(installed, locked.resolved())) {
            log.accept(describe(locked.resolved()) + " is up to date");
        } else {
            SkillResolver.Fetched fetched = resolver.fetch(locked.resolved(), request.source(), workDirectory);
            SkillOrigin origin = locked.resolved();
            installer.install(fetched.image(), new SkillOrigin(origin.id(), origin.version(), origin.status(),
                    origin.digest(), fetched.source()));
            log.accept("Installed " + describe(origin) + " (locked) from " + fetched.source());
        }
        return locked;
    }

    private static boolean matches(SkillRequest request, LockedSkill locked) {
        return request.versionSpec().equals(locked.requestedVersion())
                && request.minimumStatus() == locked.requestedStatus()
                && (request.source() == null || request.source().equals(locked.resolved().source()));
    }

    private static boolean isInstalled(SkillOrigin installed, SkillOrigin origin) {
        return installed != null && installed.id().equals(origin.id()) && installed.digest().equals(origin.digest());
    }

    private static String describe(SkillOrigin origin) {
        return origin.id() + " " + origin.version() + " (" + origin.status().value() + ") from " + origin.source();
    }

    private static void checkNames(List<SkillRequest> requests) throws SkillPullException {
        Map<String, SkillId> names = new HashMap<>();
        for (SkillRequest request : requests) {
            SkillId other = names.putIfAbsent(request.id().name(), request.id());
            if (other != null) {
                throw new SkillPullException(other.equals(request.id())
                        ? "the skill " + request.id() + " is requested twice"
                        : "the skills " + other + " and " + request.id() + " cannot both be installed - they have "
                        + "the same name");
            }
        }
    }

    private static void delete(Path path) throws SkillPullException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path each : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        } catch (IOException e) {
            throw new SkillPullException("deleting " + path + " failed: " + e, e);
        }
    }
}
