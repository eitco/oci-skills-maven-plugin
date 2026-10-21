package de.eitco.cicd.ociskills.core;

import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;
import de.eitco.cicd.ociskills.core.layer.LayerBlob;
import de.eitco.cicd.ociskills.core.layer.TarLayerBuilder;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.layout.OciLayoutWriter;
import de.eitco.cicd.ociskills.core.oci.DefaultTagStrategy;
import de.eitco.cicd.ociskills.core.oci.ImageConfigBuilder;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;
import de.eitco.cicd.ociskills.core.oci.OciJson;
import de.eitco.cicd.ociskills.core.oci.SerializedBlob;
import de.eitco.cicd.ociskills.core.oci.TagStrategy;
import de.eitco.cicd.ociskills.core.oci.WordCounter;
import de.eitco.cicd.ociskills.core.skillcard.SkillCard;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardWriter;
import de.eitco.cicd.ociskills.core.skillcard.SkillFrontmatterReader;
import de.eitco.cicd.ociskills.core.skillcard.SkillMarkdown;
import de.eitco.cicd.ociskills.core.skillcard.SkillMarkdownWriter;
import de.eitco.cicd.ociskills.core.skillcard.SkillValidator;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a skill directory into an OCI image layout. This is the core of the plugin - it knows nothing about maven,
 * nexus or any registry client.
 * <p>
 * All validations - SkillCard and symbolic links - run before anything is written. After writing, the layout is
 * read back and verified.
 */
public class SkillImageBuilder {

    private final SkillFrontmatterReader frontmatterReader = new SkillFrontmatterReader();
    private final SkillCardWriter skillCardWriter = new SkillCardWriter();
    private final SkillMarkdownWriter skillMarkdownWriter = new SkillMarkdownWriter();
    private final SkillValidator skillValidator = new SkillValidator();
    private final TarLayerBuilder tarLayerBuilder = new TarLayerBuilder();
    private final ImageConfigBuilder imageConfigBuilder = new ImageConfigBuilder();
    private final ImageManifestBuilder imageManifestBuilder = new ImageManifestBuilder();
    private final TagStrategy tagStrategy;

    public SkillImageBuilder() {
        this(new DefaultTagStrategy());
    }

    public SkillImageBuilder(TagStrategy tagStrategy) {
        this.tagStrategy = tagStrategy;
    }

    /**
     * @throws SkillValidationException if the skill is invalid - raised before anything is written
     * @throws SkillBuildException      on technical errors
     */
    public SkillBuildResult build(SkillBuildRequest request) throws SkillValidationException, SkillBuildException {
        Path skillDirectory = request.skillDirectory().toAbsolutePath().normalize();
        Path outputDirectory = request.outputDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(skillDirectory)) {
            throw new SkillValidationException("skill directory " + skillDirectory + " does not exist");
        }

        SkillMarkdown markdown = frontmatterReader.read(skillDirectory);
        skillValidator.validate(markdown.frontmatter(), request.skillCardSettings(), skillDirectory);
        SkillCard skillCard = SkillCard.of(markdown.frontmatter(), request.skillCardSettings());
        List<SkillFile> files = new ArrayList<>(new SkillFileCollector(excludingOutput(request.fileSelection(),
                skillDirectory, outputDirectory)).collect(skillDirectory));
        int wordCount = WordCounter.countWords(readPrompt(skillDirectory.resolve(SkillCard.PROMPT_FILE)));
        String tag = tagStrategy.tag(skillCard.metadata().version(), request.status());

        Path generatedDirectory = request.generatedDirectory().toAbsolutePath().normalize();
        Path skillCardFile = writeGenerated(generatedDirectory.resolve(SkillCard.FILE_NAME),
                skillCardWriter.write(skillCard));
        Path skillMarkdownFile = writeGenerated(generatedDirectory.resolve(SkillCard.PROMPT_FILE),
                skillMarkdownWriter.write(markdown, skillCard));
        files.replaceAll(file -> !file.directory() && file.path().equals(SkillCard.PROMPT_FILE)
                ? SkillFile.file(file.path(), skillMarkdownFile, file.executable())
                : file);
        files.add(SkillFile.file(SkillCard.FILE_NAME, skillCardFile, false));

        OciLayoutWriter writer = new OciLayoutWriter(outputDirectory);
        writer.prepare();

        Path temporaryLayer = writer.newTemporaryBlobFile();
        LayerBlob layer = tarLayerBuilder.build(files, request.timestamp(), temporaryLayer);
        writer.moveBlob(temporaryLayer, layer.descriptor().digest());

        SerializedBlob config = OciJson.serializeBlob(imageConfigBuilder.build(layer.diffId()), MediaTypes.IMAGE_CONFIG);
        writer.writeBlob(config.bytes(), config.descriptor().digest());

        OciImageManifest manifest = imageManifestBuilder.build(config.descriptor(), layer.descriptor(), skillCard,
                request.status(), wordCount, request.timestamp());
        SerializedBlob manifestBlob = OciJson.serializeBlob(manifest, MediaTypes.IMAGE_MANIFEST);
        writer.writeBlob(manifestBlob.bytes(), manifestBlob.descriptor().digest());
        writer.writeIndex(manifestBlob.descriptor(), tag);

        verify(outputDirectory, manifestBlob, tag);

        return new SkillBuildResult(outputDirectory, skillCard, tag, manifestBlob.descriptor(), config.descriptor(),
                layer.descriptor(), layer.diffId());
    }

    private static Path writeGenerated(Path file, byte[] content) throws SkillBuildException {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            return file;
        } catch (IOException e) {
            throw new SkillBuildException("writing " + file + " failed: " + e, e);
        }
    }

    /**
     * Makes sure the output directory never ends up in the layer, even if it is located inside the skill directory.
     */
    private static FileSelection excludingOutput(FileSelection selection, Path skillDirectory, Path outputDirectory) {
        if (!outputDirectory.startsWith(skillDirectory) || outputDirectory.equals(skillDirectory)) {
            return selection;
        }
        List<String> excludes = new ArrayList<>(selection.excludes());
        excludes.add(skillDirectory.relativize(outputDirectory).toString().replace('\\', '/') + "/**");
        return new FileSelection(selection.includes(), excludes, selection.executables());
    }

    /**
     * Reads the prompt file like go does: invalid UTF-8 sequences are replaced, never treated as white space.
     */
    private static String readPrompt(Path prompt) throws SkillBuildException {
        try {
            byte[] bytes = Files.readAllBytes(prompt);
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                throw new SkillBuildException("prompt file " + prompt + " cannot be decoded", e);
            }
        } catch (IOException e) {
            throw new SkillBuildException("prompt file " + prompt + " cannot be read: " + e, e);
        }
    }

    private static void verify(Path outputDirectory, SerializedBlob manifest, String tag) throws SkillBuildException {
        OciImageLayout layout = new OciLayoutReader().read(outputDirectory);
        if (!layout.manifestDescriptor().equals(manifest.descriptor()) || !layout.tag().equals(tag)) {
            throw new SkillBuildException("OCI image layout " + outputDirectory + " is inconsistent: expected manifest "
                    + manifest.descriptor().digest() + " tagged " + tag + " but found "
                    + layout.manifestDescriptor().digest() + " tagged " + layout.tag());
        }
    }
}
