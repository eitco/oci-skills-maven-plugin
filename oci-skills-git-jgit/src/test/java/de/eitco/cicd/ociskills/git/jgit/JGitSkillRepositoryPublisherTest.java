package de.eitco.cicd.ociskills.git.jgit;

import de.eitco.cicd.ociskills.core.SkillGitException;
import de.eitco.cicd.ociskills.core.git.GitCredentials;
import de.eitco.cicd.ociskills.core.git.GitPublishRequest;
import de.eitco.cicd.ociskills.core.git.GitPublishResult;
import de.eitco.cicd.ociskills.core.git.GitPublishResult.TagOutcome;
import de.eitco.cicd.ociskills.core.git.GitSkill;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JGitSkillRepositoryPublisherTest {

    @TempDir
    Path directory;

    private Path remote;

    @BeforeEach
    void createRemote() throws Exception {
        remote = directory.resolve("remote.git");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote.toFile()).call().close();
    }

    @Test
    void publishesIntoAnEmptyRepository() throws Exception {
        GitPublishResult result = new JGitSkillRepositoryPublisher().publish(request("demo-1.0.0",
                skill("demo", Map.of("SKILL.md", "# Demo\n", "scripts/run.sh", "#!/bin/sh\necho hi\n"))));

        assertThat(result.changed()).isTrue();
        assertThat(result.tag()).isEqualTo(TagOutcome.CREATED);
        assertThat(result.commit()).isEqualTo(head()).isEqualTo(result.tagCommit());
        assertThat(tagCommit("demo-1.0.0")).isEqualTo(result.commit());
        assertThat(files()).containsExactly(
                Map.entry("skills/demo/SKILL.md", "# Demo\n"),
                Map.entry("skills/demo/scripts/run.sh", "#!/bin/sh\necho hi\n"));
        assertThat(mode("skills/demo/scripts/run.sh")).isEqualTo(FileMode.EXECUTABLE_FILE);
        assertThat(mode("skills/demo/SKILL.md")).isEqualTo(FileMode.REGULAR_FILE);
        try (Repository repository = Git.open(remote.toFile()).getRepository(); RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve("refs/heads/main"));
            assertThat(commit.getFullMessage()).isEqualTo("release demo 1.0.0");
            assertThat(commit.getAuthorIdent().getName()).isEqualTo("skill bot");
            assertThat(commit.getAuthorIdent().getEmailAddress()).isEqualTo("bot@example.com");
        }
    }

    @Test
    void createsAMissingBranchInARepositoryWithOtherBranches() throws Exception {
        new JGitSkillRepositoryPublisher().publish(request(null, skill("other", Map.of("SKILL.md", "o1"))));

        GitPublishResult result = new JGitSkillRepositoryPublisher().publish(new GitPublishRequest(
                remote.toUri().toString(), "snapshots", "skills", List.of(skill("a", Map.of("SKILL.md", "a1"))),
                null, "snapshot", "skill bot", "bot@example.com", GitCredentials.anonymous(),
                directory.resolve("work"), 2));

        try (Repository repository = Git.open(remote.toFile()).getRepository(); RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve("refs/heads/snapshots"));
            assertThat(commit.name()).isEqualTo(result.commit());
            assertThat(commit.getParentCount()).isZero();
            assertThat(TreeWalk.forPath(repository, "skills/a/SKILL.md", commit.getTree())).isNotNull();
            assertThat(TreeWalk.forPath(repository, "skills/other/SKILL.md", commit.getTree())).isNull();
        }
        assertThat(files()).containsOnlyKeys("skills/other/SKILL.md");
    }

    @Test
    void replacesTheDirectoriesOfThePublishedSkillsAndKeepsAllOtherFiles() throws Exception {
        JGitSkillRepositoryPublisher publisher = new JGitSkillRepositoryPublisher();
        publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1", "old.md", "old")),
                skill("b", Map.of("SKILL.md", "b1"))));

        GitPublishResult result = publisher.publish(request("v2", skill("a", Map.of("SKILL.md", "a2"))));

        assertThat(result.changed()).isTrue();
        assertThat(files()).containsExactly(
                Map.entry("skills/a/SKILL.md", "a2"),
                Map.entry("skills/b/SKILL.md", "b1"));
    }

    @Test
    void createsNoCommitIfTheContentIsUnchanged() throws Exception {
        JGitSkillRepositoryPublisher publisher = new JGitSkillRepositoryPublisher();
        GitPublishResult first = publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1"))));

        GitPublishResult second = publisher.publish(request("v2", skill("a", Map.of("SKILL.md", "a1"))));

        assertThat(second.changed()).isFalse();
        assertThat(second.commit()).isEqualTo(first.commit()).isEqualTo(head());
        assertThat(second.tag()).isEqualTo(TagOutcome.CREATED);
        assertThat(tagCommit("v2")).isEqualTo(first.commit());
    }

    @Test
    void keepsAnExistingTagButPublishesTheSkillsAnyway() throws Exception {
        JGitSkillRepositoryPublisher publisher = new JGitSkillRepositoryPublisher();
        GitPublishResult first = publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1"))));

        GitPublishResult second = publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a2"))));

        assertThat(second.changed()).isTrue();
        assertThat(second.tag()).isEqualTo(TagOutcome.EXISTED);
        assertThat(second.tagCommit()).isEqualTo(first.commit());
        assertThat(second.commit()).isEqualTo(head()).isNotEqualTo(first.commit());
        assertThat(tagCommit("v1")).isEqualTo(first.commit());
        assertThat(files()).containsExactly(Map.entry("skills/a/SKILL.md", "a2"));
    }

    @Test
    void repeatingAReleaseWithTheSameContentChangesNothing() throws Exception {
        JGitSkillRepositoryPublisher publisher = new JGitSkillRepositoryPublisher();
        GitPublishResult first = publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1"))));

        GitPublishResult second = publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1"))));

        assertThat(second.changed()).isFalse();
        assertThat(second.tag()).isEqualTo(TagOutcome.EXISTED);
        assertThat(second.commit()).isEqualTo(first.commit()).isEqualTo(second.tagCommit()).isEqualTo(head());
    }

    @Test
    void createsNoTagIfNoneIsRequested() throws Exception {
        GitPublishResult result = new JGitSkillRepositoryPublisher().publish(request(null,
                skill("a", Map.of("SKILL.md", "a1"))));

        assertThat(result.tag()).isEqualTo(TagOutcome.NONE);
        assertThat(result.tagCommit()).isNull();
        try (Repository repository = Git.open(remote.toFile()).getRepository()) {
            assertThat(repository.getRefDatabase().getRefsByPrefix("refs/tags/")).isEmpty();
        }
    }

    @Test
    void retriesIfTheBranchMovedInTheMeantime() throws Exception {
        new JGitSkillRepositoryPublisher().publish(request(null, skill("other", Map.of("SKILL.md", "o1"))));
        List<Integer> attempts = new ArrayList<>();
        JGitSkillRepositoryPublisher publisher = new JGitSkillRepositoryPublisher() {
            @Override
            void beforePush(int attempt) throws Exception {
                attempts.add(attempt);
                if (attempt == 1) {
                    commitConcurrently("other/SKILL.md", "o2");
                }
            }
        };

        GitPublishResult result = publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1"))));

        assertThat(attempts).containsExactly(1, 2);
        assertThat(result.commit()).isEqualTo(head()).isEqualTo(tagCommit("v1"));
        assertThat(files()).containsExactly(
                Map.entry("skills/a/SKILL.md", "a1"),
                Map.entry("skills/other/SKILL.md", "o2"));
    }

    @Test
    void failsIfTheBranchKeepsMoving() throws Exception {
        new JGitSkillRepositoryPublisher().publish(request(null, skill("other", Map.of("SKILL.md", "o1"))));
        JGitSkillRepositoryPublisher publisher = new JGitSkillRepositoryPublisher() {
            @Override
            void beforePush(int attempt) throws Exception {
                commitConcurrently("other/SKILL.md", "o" + (attempt + 1));
            }
        };

        assertThatThrownBy(() -> publisher.publish(request("v1", skill("a", Map.of("SKILL.md", "a1")))))
                .isInstanceOf(SkillGitException.class)
                .hasMessageContaining("rejected")
                .hasMessageContaining("2 attempts");
    }

    @Test
    void reportsUnreachableRepositoriesWithoutRevealingTheCredentials() {
        GitPublishRequest request = new GitPublishRequest(directory.resolve("missing.git").toUri().toString(),
                "main", "skills", List.of(skill("a", Map.of("SKILL.md", "a1"))), null, "message", "skill bot",
                "bot@example.com", new GitCredentials("user", "s3cr3t"), directory.resolve("work"), 2);

        assertThatThrownBy(() -> new JGitSkillRepositoryPublisher().publish(request))
                .isInstanceOf(SkillGitException.class)
                .hasMessageContaining("missing.git")
                .hasMessageNotContaining("s3cr3t");
    }

    private GitPublishRequest request(String tag, GitSkill... skills) {
        return new GitPublishRequest(remote.toUri().toString(), "main", "skills", List.of(skills), tag,
                tag == null ? "snapshot" : "release demo 1.0.0", "skill bot", "bot@example.com",
                GitCredentials.anonymous(), directory.resolve("work"), 2);
    }

    private static GitSkill skill(String name, Map<String, String> files) {
        return new GitSkill(name, new FakeSkillImage(files));
    }

    /**
     * Commits a file below {@code skills/} to the remote as another build would do.
     */
    private void commitConcurrently(String path, String content) throws Exception {
        Path clone = Files.createTempDirectory(directory, "concurrent");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(clone.toFile())
                .setBranch("main").call()) {
            Path file = clone.resolve("skills").resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            git.add().addFilepattern(".").call();
            PersonIdent ident = new PersonIdent("other", "other@example.com");
            git.commit().setMessage("concurrent").setAuthor(ident).setCommitter(ident).setSign(false).call();
            git.push().call();
        }
    }

    private String head() throws Exception {
        try (Repository repository = Git.open(remote.toFile()).getRepository()) {
            return repository.resolve("refs/heads/main").name();
        }
    }

    private String tagCommit(String tag) throws Exception {
        try (Repository repository = Git.open(remote.toFile()).getRepository(); RevWalk walk = new RevWalk(repository)) {
            Ref ref = repository.exactRef("refs/tags/" + tag);
            assertThat(ref).as("tag " + tag).isNotNull();
            assertThat(walk.parseAny(ref.getObjectId()).getType()).as("annotated tag")
                    .isEqualTo(org.eclipse.jgit.lib.Constants.OBJ_TAG);
            return walk.parseCommit(ref.getObjectId()).name();
        }
    }

    private Map<String, String> files() throws Exception {
        Map<String, String> files = new TreeMap<>();
        try (Repository repository = Git.open(remote.toFile()).getRepository(); RevWalk walk = new RevWalk(repository);
             TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(walk.parseCommit(repository.resolve("refs/heads/main")).getTree());
            tree.setRecursive(true);
            while (tree.next()) {
                ObjectId blob = tree.getObjectId(0);
                files.put(tree.getPathString(), new String(repository.open(blob).getBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private FileMode mode(String path) throws Exception {
        try (Repository repository = Git.open(remote.toFile()).getRepository(); RevWalk walk = new RevWalk(repository);
             TreeWalk tree = TreeWalk.forPath(repository, path,
                     walk.parseCommit(repository.resolve("refs/heads/main")).getTree())) {
            assertThat(tree).as(path).isNotNull();
            return tree.getFileMode(0);
        }
    }
}
