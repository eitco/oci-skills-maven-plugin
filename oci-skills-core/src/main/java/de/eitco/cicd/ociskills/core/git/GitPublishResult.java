package de.eitco.cicd.ociskills.core.git;

/**
 * The result of publishing skills to a git repository.
 *
 * @param commit    the id of the commit the branch points to after publishing
 * @param changed   whether a commit was created - {@code false} if the repository already contained the skills
 * @param tag       what happened to the requested tag
 * @param tagCommit the id of the commit the tag points to - {@code null} without tag
 */
public record GitPublishResult(String commit, boolean changed, TagOutcome tag, String tagCommit) {

    public enum TagOutcome {
        /**
         * no tag was requested
         */
        NONE,
        /**
         * the tag was created and points to {@link #commit()}
         */
        CREATED,
        /**
         * the tag existed already and was left unchanged - it may point to another commit than {@link #commit()}
         */
        EXISTED
    }
}
