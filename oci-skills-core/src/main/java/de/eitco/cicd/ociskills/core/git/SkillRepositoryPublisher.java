package de.eitco.cicd.ociskills.core.git;

import de.eitco.cicd.ociskills.core.SkillGitException;

/**
 * Publishes skills to a git repository, so tools reading skills from git - e.g. {@code npx skills} - can install
 * them. Implementations clone the branch, replace the directories of the given skills with the files of their images,
 * commit if anything changed, tag and push.
 * <p>
 * An existing tag is never moved or replaced: the skills are published to the branch anyway and
 * {@link GitPublishResult.TagOutcome#EXISTED} is reported, so release builds can be repeated.
 */
public interface SkillRepositoryPublisher {

    GitPublishResult publish(GitPublishRequest request) throws SkillGitException;
}
