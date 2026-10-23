package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.SkillValidationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the YAML test specifications of a skill, e.g. {@code tests.yaml}. Unknown properties
 * are errors, so typos do not silently disable an assertion.
 */
public final class SpecReader {

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private SpecReader() {
    }

    /**
     * @throws SkillValidationException if the file cannot be read or does not match the type
     */
    public static <T> T read(Path file, Class<T> type) throws SkillValidationException {
        if (!Files.isRegularFile(file)) {
            throw new SkillValidationException(file + " does not exist");
        }
        try {
            T value = MAPPER.readValue(file, type);
            if (value == null) {
                throw new SkillValidationException(file + " is empty");
            }
            return value;
        } catch (JacksonException e) {
            throw new SkillValidationException(file + " is invalid: " + e.getOriginalMessage()
                    + (e.getCause() != null && e.getCause().getMessage() != null ? " - " + e.getCause().getMessage() : ""));
        }
    }
}
