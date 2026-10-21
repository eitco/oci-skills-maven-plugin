package de.eitco.cicd.ociskills.core.layout;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageIndex;
import de.eitco.cicd.ociskills.core.oci.OciJson;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Writes an OCI image layout. Blobs are stored under {@code blobs/sha256/<hex>}.
 */
public class OciLayoutWriter {

    static final String IMAGE_LAYOUT_VERSION = "1.0.0";

    private final Path directory;
    private final Path blobDirectory;

    public OciLayoutWriter(Path directory) {
        this.directory = directory;
        this.blobDirectory = directory.resolve(OciImageLayout.BLOBS_DIRECTORY).resolve(Digest.SHA_256);
    }

    /**
     * Removes the content of a previous layout ({@code oci-layout}, {@code index.json} and all blobs) and creates
     * the directory structure. Other files in the directory are left untouched.
     */
    public void prepare() throws SkillBuildException {
        try {
            Files.deleteIfExists(directory.resolve(OciImageLayout.INDEX_FILE));
            Files.deleteIfExists(directory.resolve(OciImageLayout.OCI_LAYOUT_FILE));
            if (Files.isDirectory(blobDirectory)) {
                try (DirectoryStream<Path> blobs = Files.newDirectoryStream(blobDirectory)) {
                    for (Path blob : blobs) {
                        Files.delete(blob);
                    }
                }
            }
            Files.createDirectories(blobDirectory);
        } catch (IOException e) {
            throw new SkillBuildException("preparing OCI image layout " + directory + " failed: " + e, e);
        }
    }

    /**
     * @return a temporary file inside the layout to write a blob into before its digest is known
     */
    public Path newTemporaryBlobFile() throws SkillBuildException {
        try {
            return Files.createTempFile(blobDirectory, ".blob-", ".tmp");
        } catch (IOException e) {
            throw new SkillBuildException("creating temporary file in " + blobDirectory + " failed: " + e, e);
        }
    }

    /**
     * Moves a temporary blob file to its final, content addressed location.
     */
    public void moveBlob(Path temporaryFile, Digest digest) throws SkillBuildException {
        try {
            Files.move(temporaryFile, blobDirectory.resolve(digest.hex()), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new SkillBuildException("storing blob " + digest + " failed: " + e, e);
        }
    }

    public void writeBlob(byte[] content, Digest digest) throws SkillBuildException {
        try {
            Files.write(blobDirectory.resolve(digest.hex()), content);
        } catch (IOException e) {
            throw new SkillBuildException("storing blob " + digest + " failed: " + e, e);
        }
    }

    /**
     * Writes {@code oci-layout} and {@code index.json} referencing the given manifest under the given tag.
     */
    public void writeIndex(OciDescriptor manifestDescriptor, String tag) throws SkillBuildException {
        OciImageIndex index = new OciImageIndex(2, MediaTypes.IMAGE_INDEX, List.of(
                manifestDescriptor.withAnnotations(Map.of(ImageManifestBuilder.ANNOTATION_REF_NAME, tag))));
        try {
            Files.write(directory.resolve(OciImageLayout.OCI_LAYOUT_FILE),
                    OciJson.serialize(Map.of("imageLayoutVersion", IMAGE_LAYOUT_VERSION)));
            Files.write(directory.resolve(OciImageLayout.INDEX_FILE), OciJson.serialize(index));
        } catch (IOException e) {
            throw new SkillBuildException("writing index of OCI image layout " + directory + " failed: " + e, e);
        }
    }
}
