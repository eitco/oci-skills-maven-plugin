package de.eitco.cicd.ociskills.core.git;

import java.nio.file.Path;
import java.util.List;

/**
 * Describes publishing skills to a git repository.
 *
 * @param url           the url of the repository, e.g. {@code https://git.example.com/ai/skills.git}
 * @param branch        the branch to commit to - created if missing
 * @param directory     the directory in the repository containing one subdirectory per skill, e.g. {@code skills}
 * @param skills        the skills to publish - their directories are replaced, all other files are kept
 * @param tag           the tag to create - {@code null} to create none
 * @param message       the message of the commit and the tag
 * @param authorName    the name of the author and committer
 * @param authorEmail   the email of the author and committer
 * @param credentials   the credentials for fetching and pushing
 * @param workDirectory the directory to clone into - deleted before cloning
 * @param attempts      how often to try when the push is rejected because the branch moved in the meantime
 */
public record GitPublishRequest(String url, String branch, String directory, List<GitSkill> skills, String tag,
                                String message, String authorName, String authorEmail, GitCredentials credentials,
                                Path workDirectory, int attempts) {
}
