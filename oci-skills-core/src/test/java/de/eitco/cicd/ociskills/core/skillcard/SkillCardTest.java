package de.eitco.cicd.ociskills.core.skillcard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCardTest {

    @Test
    void combinesFrontmatterAndSettings() {
        SkillCard card = SkillCard.of(new SkillFrontmatter("nexus-test", "Test skill"),
                new SkillCardSettings("cicd", "1.0.0-SNAPSHOT"));

        assertThat(card).isEqualTo(new SkillCard("skillimage.io/v1alpha1", "SkillCard",
                new SkillMetadata("nexus-test", "cicd", "1.0.0-SNAPSHOT", "Test skill"), new SkillSpec("SKILL.md")));
    }
}
