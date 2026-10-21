package de.eitco.cicd.ociskills.core;

import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * The input of {@link SkillImageBuilder}.
 *
 * @param skillDirectory     the skill directory containing {@code SKILL.md}
 * @param outputDirectory    the directory the OCI image layout is written to
 * @param fileSelection      the files to put into the layer
 * @param status             the lifecycle status of the image
 * @param timestamp          the timestamp used for all tar entries and the {@code created} annotation
 * @param skillCardSettings  the values of the generated {@code skill.yaml} that are not part of {@code SKILL.md}
 * @param generatedDirectory the directory the generated {@code skill.yaml} and {@code SKILL.md} are written to
 *                           before they are put into the layer
 */
public record SkillBuildRequest(Path skillDirectory, Path outputDirectory, FileSelection fileSelection,
                                SkillStatus status, Instant timestamp, SkillCardSettings skillCardSettings,
                                Path generatedDirectory) {

    public SkillBuildRequest {
        Objects.requireNonNull(skillDirectory, "skillDirectory");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(skillCardSettings, "skillCardSettings");
        Objects.requireNonNull(generatedDirectory, "generatedDirectory");
        fileSelection = fileSelection == null ? FileSelection.defaults() : fileSelection;
        status = status == null ? SkillStatus.DRAFT : status;
    }
}
