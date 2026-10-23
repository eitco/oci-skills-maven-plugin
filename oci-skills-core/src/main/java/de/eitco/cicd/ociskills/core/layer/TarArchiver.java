package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Writes a directory - e.g. the OCI image layouts of all skills - into an uncompressed, reproducible tar file:
 * entries are sorted, owned by root and stamped with the given time.
 */
public class TarArchiver {

    private static final FileSelection ALL_FILES = new FileSelection(null, List.of(), null);

    /**
     * @param directory        the directory to archive - entries are relative to it
     * @param modificationTime the modification time of all entries
     * @param target           the tar file to write
     */
    public void archive(Path directory, Instant modificationTime, Path target) throws SkillBuildException {
        List<SkillFile> files;
        try {
            files = new SkillFileCollector(ALL_FILES).collect(directory);
        } catch (SkillValidationException e) {
            throw new SkillBuildException(e.getMessage(), e);
        }
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                TarWriter tar = new TarWriter(out, modificationTime);
                for (SkillFile file : files) {
                    if (file.directory()) {
                        tar.writeDirectory(file.path(), TarLayerBuilder.DIRECTORY_MODE);
                    } else {
                        try (InputStream content = Files.newInputStream(file.source())) {
                            tar.writeFile(file.path(), file.executable() ? TarLayerBuilder.EXECUTABLE_MODE
                                    : TarLayerBuilder.FILE_MODE, Files.size(file.source()), content);
                        }
                    }
                }
                tar.finish();
            }
        } catch (IOException e) {
            throw new SkillBuildException("writing " + target + " failed: " + e, e);
        }
    }
}
