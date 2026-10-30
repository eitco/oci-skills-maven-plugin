package de.eitco.cicd.ociskills.core.layout;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;

import java.nio.file.Path;

/**
 * A verified skill image - no matter if it is stored as OCI image layout or as skill jar.
 */
public interface SkillImage {

    /**
     * @return the descriptor of the manifest - its digest identifies the image
     */
    OciDescriptor manifestDescriptor();

    OciImageManifest manifest();

    /**
     * Writes the files of the skill into the given directory.
     *
     * @param target the directory to write to - created if missing
     */
    void extract(Path target) throws SkillBuildException;
}
