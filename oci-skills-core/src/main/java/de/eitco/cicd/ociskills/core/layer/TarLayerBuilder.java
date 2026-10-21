package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the skill files into a single reproducible, gzip compressed tar layer. The data is streamed - neither the
 * tar nor the compressed layer is held in memory. Both digests are computed while writing.
 * <p>
 * Normalization: entries sorted by path, uid/gid {@code 0}, empty user and group names, mode {@code 0644} for
 * regular files, {@code 0755} for directories and executables, the given modification time for every entry and a
 * gzip header without timestamp.
 */
public class TarLayerBuilder {

    public static final int FILE_MODE = 0644;
    public static final int EXECUTABLE_MODE = 0755;
    public static final int DIRECTORY_MODE = 0755;

    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * @param files            the files to write
     * @param modificationTime the modification time of every entry
     * @param target           the file the compressed layer is written to
     */
    public LayerBlob build(List<SkillFile> files, Instant modificationTime, Path target) throws SkillBuildException {
        List<SkillFile> sorted = files.stream()
                .sorted(Comparator.comparing(SkillFile::path, SkillFileCollector.PATH_ORDER))
                .toList();
        DigestingOutputStream compressed;
        DigestingOutputStream uncompressed;
        try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE);
             DigestingOutputStream compressedStream = new DigestingOutputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(compressedStream, BUFFER_SIZE);
             DigestingOutputStream uncompressedStream = new DigestingOutputStream(gzip)) {
            compressed = compressedStream;
            uncompressed = uncompressedStream;
            TarWriter tar = new TarWriter(uncompressed, modificationTime);
            for (SkillFile skillFile : sorted) {
                write(tar, skillFile);
            }
            tar.finish();
        } catch (IOException e) {
            throw new SkillBuildException("writing layer " + target + " failed: " + e.getMessage(), e);
        }
        return new LayerBlob(target,
                new OciDescriptor(MediaTypes.IMAGE_LAYER_TAR_GZIP, compressed.digest(), compressed.size()),
                uncompressed.digest());
    }

    private static void write(TarWriter tar, SkillFile skillFile) throws SkillBuildException, IOException {
        if (skillFile.directory()) {
            tar.writeDirectory(skillFile.path(), DIRECTORY_MODE);
            return;
        }
        long size;
        try {
            size = Files.size(skillFile.source());
        } catch (IOException e) {
            throw new SkillBuildException("file " + skillFile.path() + " cannot be read: " + e, e);
        }
        try (InputStream content = Files.newInputStream(skillFile.source())) {
            tar.writeFile(skillFile.path(), skillFile.executable() ? EXECUTABLE_MODE : FILE_MODE, size, content);
        }
    }
}
