package de.eitco.cicd.ociskills.git.jgit;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillGitException;
import de.eitco.cicd.ociskills.core.files.FileTrees;
import de.eitco.cicd.ociskills.core.git.GitCredentials;
import de.eitco.cicd.ociskills.core.git.GitPublishRequest;
import de.eitco.cicd.ociskills.core.git.GitPublishResult;
import de.eitco.cicd.ociskills.core.git.GitPublishResult.TagOutcome;
import de.eitco.cicd.ociskills.core.git.GitSkill;
import de.eitco.cicd.ociskills.core.git.SkillRepositoryPublisher;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Publishes skills to a git repository using JGit - no git installation is needed. The branch is cloned shallowly,
 * the directories of the skills are replaced, a commit is created only if anything changed and the tag is only
 * created if it does not exist yet. A push rejected because the branch moved in the meantime is repeated on a fresh
 * clone.
 */
public class JGitSkillRepositoryPublisher implements SkillRepositoryPublisher {

    @Override
    public GitPublishResult publish(GitPublishRequest request) throws SkillGitException {
        if (request.skills().isEmpty()) {
            throw new SkillGitException("no skills to publish to " + request.url());
        }
        CredentialsProvider credentials = credentials(request.credentials());
        int attempts = Math.max(1, request.attempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Optional<GitPublishResult> result = attempt(request, credentials, attempt);
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw new SkillGitException("pushing " + request.branch() + " to " + request.url() + " was rejected - the "
                + "branch kept moving, giving up after " + attempts + " attempts");
    }

    /**
     * Called right before pushing the branch - a hook for tests.
     */
    void beforePush(int attempt) throws Exception {
    }

    /**
     * The branch is pushed before the tag, so the tag never points to a commit the branch push was rejected for.
     *
     * @return empty if the branch moved in the meantime and the attempt has to be repeated
     */
    private Optional<GitPublishResult> attempt(GitPublishRequest request, CredentialsProvider credentials,
                                               int attempt) throws SkillGitException {
        String branchRef = Constants.R_HEADS + request.branch();
        try (Git git = clone(request, credentials)) {
            boolean changed = stage(git, replaceSkills(request, request.workDirectory()));
            PersonIdent ident = new PersonIdent(request.authorName(), request.authorEmail());
            if (changed) {
                git.commit().setMessage(request.message()).setAuthor(ident).setCommitter(ident).setSign(false)
                        .setNoVerify(true).call();
                beforePush(attempt);
                if (!push(git, request, credentials, branchRef)) {
                    return Optional.empty();
                }
            }
            ObjectId commit = git.getRepository().resolve(branchRef);
            if (request.tag() == null) {
                return Optional.of(new GitPublishResult(commit.name(), changed, TagOutcome.NONE, null));
            }
            ObjectId existing = remoteTag(request, credentials);
            if (existing == null) {
                try (RevWalk walk = new RevWalk(git.getRepository())) {
                    git.tag().setName(request.tag()).setMessage(request.message()).setTagger(ident)
                            .setAnnotated(true).setSigned(false).setObjectId(walk.parseCommit(commit)).call();
                }
                SkillGitException rejection = null;
                try {
                    if (push(git, request, credentials, Constants.R_TAGS + request.tag())) {
                        return Optional.of(new GitPublishResult(commit.name(), changed, TagOutcome.CREATED,
                                commit.name()));
                    }
                } catch (SkillGitException e) {
                    rejection = e;
                }
                // servers report a tag another build created in the meantime differently
                existing = remoteTag(request, credentials);
                if (existing == null) {
                    throw rejection != null ? rejection : new SkillGitException("pushing the tag " + request.tag()
                            + " to " + request.url() + " was rejected");
                }
            }
            return Optional.of(new GitPublishResult(commit.name(), changed, TagOutcome.EXISTED, existing.name()));
        } catch (SkillGitException e) {
            throw e;
        } catch (Exception e) {
            throw new SkillGitException("publishing to " + request.url() + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * @return {@code false} if the push was rejected because the remote ref moved or exists already
     */
    private static boolean push(Git git, GitPublishRequest request, CredentialsProvider credentials, String ref)
            throws GitAPIException, SkillGitException {
        Iterable<PushResult> results = git.push().setRemote(Constants.DEFAULT_REMOTE_NAME)
                .setRefSpecs(new RefSpec(ref + ":" + ref)).setCredentialsProvider(credentials).call();
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                switch (update.getStatus()) {
                    case OK, UP_TO_DATE -> {
                    }
                    case REJECTED_NONFASTFORWARD, REJECTED_REMOTE_CHANGED -> {
                        return false;
                    }
                    default -> throw new SkillGitException("pushing " + update.getRemoteName() + " to "
                            + request.url() + " failed: " + update.getStatus()
                            + (update.getMessage() == null ? "" : " - " + update.getMessage()));
                }
            }
        }
        return true;
    }

    private static Git clone(GitPublishRequest request, CredentialsProvider credentials) throws SkillGitException {
        Path work = request.workDirectory();
        String branchRef = Constants.R_HEADS + request.branch();
        try {
            FileTrees.deleteRecursively(work);
            Files.createDirectories(work);
            boolean branchExists = !Git.lsRemoteRepository().setRemote(request.url()).setHeads(true)
                    .setCredentialsProvider(credentials).call().stream()
                    .filter(ref -> ref.getName().equals(branchRef)).toList().isEmpty();
            Git git;
            if (branchExists) {
                git = Git.cloneRepository().setURI(request.url()).setDirectory(work.toFile())
                        .setBranchesToClone(List.of(branchRef)).setBranch(branchRef).setDepth(1)
                        .setCredentialsProvider(credentials).call();
            } else {
                // empty repository or missing branch: the first commit creates it
                git = Git.init().setDirectory(work.toFile()).setInitialBranch(request.branch()).call();
                git.remoteAdd().setName(Constants.DEFAULT_REMOTE_NAME).setUri(new URIish(request.url())).call();
            }
            StoredConfig config = git.getRepository().getConfig();
            // the files have to be committed byte for byte with their executable bits - no matter the user config
            config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
            config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_FILEMODE, true);
            config.save();
            return git;
        } catch (GitAPIException | IOException | URISyntaxException e) {
            throw new SkillGitException("cloning " + request.branch() + " of " + request.url() + " failed: "
                    + e.getMessage(), e);
        }
    }

    /**
     * @return the paths of the skill directories relative to the work tree
     */
    private static List<String> replaceSkills(GitPublishRequest request, Path work)
            throws SkillGitException, IOException {
        List<String> paths = new ArrayList<>();
        for (GitSkill skill : request.skills()) {
            String path = request.directory() == null || request.directory().isBlank() ? skill.name()
                    : request.directory().replaceAll("^/+|/+$", "") + "/" + skill.name();
            Path target;
            try {
                target = FileTrees.resolveEntry(work, path);
            } catch (SkillBuildException e) {
                throw new SkillGitException("the skill " + skill.name() + " cannot be published to " + path + ": "
                        + e.getMessage(), e);
            }
            FileTrees.deleteRecursively(target);
            try {
                skill.image().extract(target);
            } catch (SkillBuildException e) {
                throw new SkillGitException("extracting the skill " + skill.name() + " failed: " + e.getMessage(), e);
            }
            paths.add(path);
        }
        return paths;
    }

    /**
     * Stages new, changed and deleted files of the given paths.
     *
     * @return whether anything changed
     */
    private static boolean stage(Git git, Collection<String> paths) throws GitAPIException {
        AddCommand add = git.add();
        AddCommand update = git.add().setUpdate(true);
        for (String path : paths) {
            add.addFilepattern(path);
            update.addFilepattern(path);
        }
        add.call();
        update.call();
        Status status = git.status().call();
        return !status.getAdded().isEmpty() || !status.getChanged().isEmpty() || !status.getRemoved().isEmpty();
    }

    /**
     * @return the commit the tag points to - {@code null} if the remote has no such tag
     */
    private static ObjectId remoteTag(GitPublishRequest request, CredentialsProvider credentials)
            throws GitAPIException {
        Collection<Ref> tags = Git.lsRemoteRepository().setRemote(request.url()).setTags(true).setHeads(false)
                .setCredentialsProvider(credentials).call();
        for (Ref tag : tags) {
            if (tag.getName().equals(Constants.R_TAGS + request.tag())) {
                return tag.getPeeledObjectId() != null ? tag.getPeeledObjectId() : tag.getObjectId();
            }
        }
        return null;
    }

    private static CredentialsProvider credentials(GitCredentials credentials) {
        if (credentials == null || credentials.isAnonymous()) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(credentials.username(),
                credentials.password() == null ? "" : credentials.password());
    }
}
