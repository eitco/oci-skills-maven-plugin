package de.eitco.cicd.ociskills.core.git;

import de.eitco.cicd.ociskills.core.layout.SkillImage;

/**
 * A skill to publish to a git repository.
 *
 * @param name  the name of the skill - the name of its directory in the repository
 * @param image the image whose files are published
 */
public record GitSkill(String name, SkillImage image) {
}
