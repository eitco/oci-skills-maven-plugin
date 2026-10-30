package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillOrigin;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves requested skills against an ordered list of sources and fetches locked skills from any of them.
 * <p>
 * Resolving asks the sources in their order - the first source knowing a matching version wins. Within it, the
 * highest version with an accepted status is taken, preferring the more mature status if a version has several.
 * Fetching a locked skill tries the source it was resolved from first, then all others - each of them has to
 * deliver exactly the locked digest. A skill requested with a location is resolved and fetched from there only.
 * Every image has to contain the requested skill.
 */
final class SkillResolver {

    private final List<SkillSource> sources;

    SkillResolver(List<SkillSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /**
     * @param directory a directory to write fetched images to
     */
    Resolved resolve(SkillRequest request, Path directory) throws SkillPullException {
        SkillId id = request.id();
        String versionSpec = request.versionSpec();
        List<SkillStatus> accepted = accepted(request.minimumStatus());
        List<SkillSource> candidates = sources(request);
        for (SkillSource source : candidates) {
            List<String> versions = source.versions(id, request.repository(), versionSpec).stream()
                    .sorted(SkillVersions.ORDER.reversed())
                    .toList();
            for (String version : versions) {
                for (SkillStatus status : accepted) {
                    Optional<SkillImage> image = source.fetch(id, request.repository(), version, status, null,
                            newDirectory(directory));
                    if (image.isPresent()) {
                        checkId(source, id, image.get());
                        return new Resolved(new SkillOrigin(id, version, status,
                                image.get().manifestDescriptor().digest(), source.id()), image.get());
                    }
                }
            }
        }
        throw new SkillPullException("no version " + versionSpec + " of " + id + " with status "
                + accepted.stream().map(SkillStatus::value).collect(Collectors.joining(" or "))
                + " found in the sources " + ids(candidates));
    }

    /**
     * @param request   the request the skill was locked for
     * @param directory a directory to write fetched images to
     * @return the image of the locked skill - a source delivering another digest is an error
     */
    Fetched fetch(SkillOrigin locked, SkillRequest request, Path directory) throws SkillPullException {
        List<SkillSource> candidates = new ArrayList<>(sources(request));
        candidates.sort(Comparator.comparing(source -> !source.id().equals(locked.source())));
        for (SkillSource source : candidates) {
            Optional<SkillImage> image = source.fetch(locked.id(), request.repository(), locked.version(),
                    locked.status(), locked.digest(), newDirectory(directory));
            if (image.isPresent()) {
                checkId(source, locked.id(), image.get());
                if (!image.get().manifestDescriptor().digest().equals(locked.digest())) {
                    throw new SkillPullException("the source " + source.id() + " has another image of "
                            + locked.id() + " " + locked.version() + " (" + locked.status().value() + ") than the lock "
                            + "file: " + image.get().manifestDescriptor().digest() + " instead of " + locked.digest()
                            + " - if the skill was rebuilt intentionally, run oci-skills:update");
                }
                return new Fetched(source.id(), image.get());
            }
        }
        throw new SkillPullException("the locked image " + locked.digest() + " of " + locked.id() + " "
                + locked.version() + " (" + locked.status().value() + ") was not found in the sources "
                + ids(candidates) + " - run oci-skills:update to resolve it again");
    }

    /**
     * @return the accepted statuses - most mature first
     */
    static List<SkillStatus> accepted(SkillStatus minimumStatus) {
        return Arrays.stream(SkillStatus.values())
                .filter(status -> status.ordinal() >= minimumStatus.ordinal())
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private List<SkillSource> sources(SkillRequest request) throws SkillPullException {
        if (request.location() != null) {
            return List.of(request.location());
        }
        String sourceId = request.source();
        if (sourceId == null || sourceId.isBlank()) {
            return sources;
        }
        return sources.stream()
                .filter(source -> source.id().equals(sourceId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new SkillPullException("the source " + sourceId + " is not configured - "
                        + "configured sources: " + ids(sources)));
    }

    private static void checkId(SkillSource source, SkillId requested, SkillImage image) throws SkillPullException {
        SkillId found;
        try {
            found = SkillAnnotations.id(image);
        } catch (IllegalArgumentException e) {
            throw new SkillPullException("the source " + source.id() + " delivered no skill image built by the "
                    + "plugin for " + requested + ": " + e.getMessage(), e);
        }
        if (!found.equals(requested)) {
            throw new SkillPullException("the source " + source.id() + " delivered the skill " + found
                    + " instead of " + requested);
        }
    }

    private static String ids(List<SkillSource> sources) {
        return sources.stream().map(SkillSource::id).collect(Collectors.joining(", "));
    }

    private static Path newDirectory(Path directory) throws SkillPullException {
        try {
            return Files.createTempDirectory(Files.createDirectories(directory), "fetch-");
        } catch (IOException e) {
            throw new SkillPullException("creating a directory in " + directory + " failed: " + e, e);
        }
    }

    /**
     * @param origin the resolved skill
     * @param image  its image
     */
    record Resolved(SkillOrigin origin, SkillImage image) {
    }

    /**
     * @param source the id of the source that delivered the image
     * @param image  the image
     */
    record Fetched(String source, SkillImage image) {
    }
}
