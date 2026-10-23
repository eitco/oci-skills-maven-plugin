package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillTestException;
import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.files.FileTrees;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies the files of a skill that go into the image - including the executable bit - into a separate directory, so
 * tests never run against or modify the sources.
 */
public class SkillStager {

    /**
     * @param skillDirectory the skill directory
     * @param selection      the files that go into the image
     * @param target         the directory to copy to - its previous content is removed
     * @return {@code target}
     */
    public Path stage(Path skillDirectory, FileSelection selection, Path target) throws SkillTestException {
        try {
            FileTrees.deleteRecursively(target);
            Files.createDirectories(target);
            for (SkillFile file : new SkillFileCollector(selection).collect(skillDirectory)) {
                Path copy = target.resolve(file.path());
                if (file.directory()) {
                    Files.createDirectories(copy);
                } else {
                    Files.createDirectories(copy.getParent());
                    Files.copy(file.source(), copy, StandardCopyOption.REPLACE_EXISTING);
                    FileTrees.setExecutable(copy, file.executable());
                }
            }
            return target;
        } catch (IOException | SkillValidationException | SkillBuildException e) {
            throw new SkillTestException("staging " + skillDirectory + " to " + target + " failed: " + e.getMessage(),
                    e);
        }
    }
}
