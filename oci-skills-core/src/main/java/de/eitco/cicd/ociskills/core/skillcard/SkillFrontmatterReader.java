package de.eitco.cicd.ociskills.core.skillcard;

import de.eitco.cicd.ociskills.core.SkillValidationException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Reads the {@code SKILL.md} of a skill directory: the YAML frontmatter - the block between a first line {@code ---}
 * and the next line {@code ---} - and the unchanged bytes after it.
 */
public class SkillFrontmatterReader {

    private static final String DELIMITER = "---";

    private static final byte[] BYTE_ORDER_MARK = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};

    /**
     * @param skillDirectory the skill directory containing {@code SKILL.md}
     * @return the parsed {@code SKILL.md} - not yet validated
     * @throws SkillValidationException if {@code SKILL.md} is missing or has no valid frontmatter
     */
    public SkillMarkdown read(Path skillDirectory) throws SkillValidationException {
        Path file = skillDirectory.resolve(SkillCard.PROMPT_FILE);
        byte[] content = readContent(file);
        int start = startsWith(content, BYTE_ORDER_MARK) ? BYTE_ORDER_MARK.length : 0;
        int firstLineEnd = lineEnd(content, start);
        if (start == content.length || !isDelimiter(content, start, firstLineEnd)) {
            throw new SkillValidationException(file + " has no frontmatter - it must start with a line '---', "
                    + "followed by 'name' and 'description' and a closing line '---'");
        }
        int yamlStart = next(content, firstLineEnd);
        int lineStart = yamlStart;
        while (lineStart < content.length && !isDelimiter(content, lineStart, lineEnd(content, lineStart))) {
            lineStart = next(content, lineEnd(content, lineStart));
        }
        if (lineStart >= content.length) {
            throw new SkillValidationException("the frontmatter of " + file
                    + " is not terminated - add a closing line '---'");
        }
        String yaml = decode(content, yamlStart, lineStart);
        byte[] body = Arrays.copyOfRange(content, next(content, lineEnd(content, lineStart)), content.length);
        if (yaml.isBlank()) {
            throw new SkillValidationException("the frontmatter of " + file + " is empty");
        }
        try {
            SkillFrontmatter frontmatter = Yaml.READER.readValue(yaml, SkillFrontmatter.class);
            if (frontmatter == null) {
                throw new SkillValidationException("the frontmatter of " + file + " is empty");
            }
            return new SkillMarkdown(frontmatter, yaml, body);
        } catch (JacksonException e) {
            throw new SkillValidationException(file + " is not a valid frontmatter" + location(e) + ": "
                    + e.getOriginalMessage());
        }
    }

    private static byte[] readContent(Path file) throws SkillValidationException {
        if (!Files.isRegularFile(file)) {
            throw new SkillValidationException(file + " does not exist - every skill needs a " + SkillCard.PROMPT_FILE
                    + " with a frontmatter containing 'name' and 'description'");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new SkillValidationException(file + " cannot be read: " + e.getMessage());
        }
    }

    /**
     * @return the index of the {@code \n} ending the line starting at {@code start}, or the end of the content
     */
    private static int lineEnd(byte[] content, int start) {
        int end = start;
        while (end < content.length && content[end] != '\n') {
            end++;
        }
        return end;
    }

    /**
     * @return the start of the line after the line ending at {@code lineEnd}
     */
    private static int next(byte[] content, int lineEnd) {
        return Math.min(lineEnd + 1, content.length);
    }

    private static boolean isDelimiter(byte[] content, int start, int end) {
        return decode(content, start, end).strip().equals(DELIMITER);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    /**
     * Decodes like the word count does: invalid UTF-8 sequences are replaced.
     */
    private static String decode(byte[] content, int start, int end) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(content, start, end - start))
                    .toString();
        } catch (CharacterCodingException e) {
            // cannot happen with CodingErrorAction.REPLACE
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return the location in {@code SKILL.md} - the frontmatter starts in line 2
     */
    private static String location(JacksonException exception) {
        TokenStreamLocation location = exception.getLocation();
        if (location == null || location.getLineNr() < 0) {
            return "";
        }
        return " (line " + (location.getLineNr() + 1) + ", column " + location.getColumnNr() + ")";
    }
}
