package de.eitco.cicd.ociskills.core.skillcard;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * The YAML configuration shared by the readers and writers of {@code skill.yaml} and the {@code SKILL.md}
 * frontmatter.
 */
final class Yaml {

    /**
     * Ignores unknown fields. Numbers keep their scale (e.g. {@code 1.10}) when read into a tree and written back.
     */
    static final YAMLMapper READER = YAMLMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    /**
     * Writes like skillctl: fields in declaration order, no document start marker, no line splitting and quotes only
     * where YAML needs them. Strings that look like numbers, e.g. a version {@code 1.10}, are quoted.
     */
    static final YAMLMapper WRITER = YAMLMapper.builder()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .disable(YAMLWriteFeature.SPLIT_LINES)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            .build();

    private Yaml() {
    }
}
