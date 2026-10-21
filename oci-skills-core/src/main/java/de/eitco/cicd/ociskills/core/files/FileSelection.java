package de.eitco.cicd.ociskills.core.files;

import java.util.List;

/**
 * Configures which files of the skill directory go into the layer.
 *
 * @param includes    ant style patterns of files to include
 * @param excludes    ant style patterns of files to exclude - excludes win over includes
 * @param executables ant style patterns of files that are executable regardless of their file system permissions
 */
public record FileSelection(List<String> includes, List<String> excludes, List<String> executables) {

    public static final List<String> DEFAULT_INCLUDES = List.of("**/*");
    public static final List<String> DEFAULT_EXCLUDES = List.of(".git/**", "target/**");

    public FileSelection {
        includes = includes == null || includes.isEmpty() ? DEFAULT_INCLUDES : List.copyOf(includes);
        excludes = excludes == null ? DEFAULT_EXCLUDES : List.copyOf(excludes);
        executables = executables == null ? List.of() : List.copyOf(executables);
    }

    public static FileSelection defaults() {
        return new FileSelection(null, null, null);
    }
}
