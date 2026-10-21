package de.eitco.cicd.ociskills.core.files;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillValidationException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Determines the files of a skill directory that go into the layer. Symbolic links are resolved if - and only if -
 * they point to a location inside the skill directory.
 */
public class SkillFileCollector {

    /**
     * Orders paths by their UTF-8 bytes - independent of locale and file system order.
     */
    public static final Comparator<String> PATH_ORDER = (first, second) ->
            Arrays.compareUnsigned(first.getBytes(UTF_8), second.getBytes(UTF_8));

    private final List<AntPattern> includes;
    private final List<AntPattern> excludes;
    private final List<AntPattern> executables;

    public SkillFileCollector(FileSelection selection) {
        this.includes = selection.includes().stream().map(AntPattern::compile).toList();
        this.excludes = selection.excludes().stream().map(AntPattern::compile).toList();
        this.executables = selection.executables().stream().map(AntPattern::compile).toList();
    }

    /**
     * @param skillDirectory the skill directory
     * @return the selected files and their parent directories, sorted by path
     * @throws SkillValidationException if a symbolic link points outside of the skill directory
     * @throws SkillBuildException      if the directory cannot be read
     */
    public List<SkillFile> collect(Path skillDirectory) throws SkillValidationException, SkillBuildException {
        Path root = skillDirectory.toAbsolutePath().normalize();
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException e) {
            throw new SkillBuildException("skill directory " + root + " cannot be read", e);
        }

        Map<String, SkillFile> files = new TreeMap<>(PATH_ORDER);
        List<String> problems = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                                throws IOException {
                            if (directory.equals(root)) {
                                return FileVisitResult.CONTINUE;
                            }
                            String path = relativePath(root, directory);
                            if (isExcluded(path)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (!directory.toRealPath().startsWith(realRoot)) {
                                problems.add(path + " is a symbolic link pointing outside of the skill directory");
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                            String path = relativePath(root, file);
                            if (!attributes.isRegularFile() || isExcluded(path) || !isIncluded(path)) {
                                return FileVisitResult.CONTINUE;
                            }
                            Path realFile = file.toRealPath();
                            if (!realFile.startsWith(realRoot)) {
                                problems.add(path + " is a symbolic link pointing outside of the skill directory");
                                return FileVisitResult.CONTINUE;
                            }
                            files.put(path, SkillFile.file(path, realFile, isExecutable(path, realFile)));
                            addParentDirectories(root, file.getParent(), files);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                            String path = relativePath(root, file);
                            if (isExcluded(path)) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (exception instanceof FileSystemLoopException) {
                                problems.add(path + " is a symbolic link creating a cycle");
                                return FileVisitResult.CONTINUE;
                            }
                            if (Files.isSymbolicLink(file)) {
                                problems.add(path + " is a broken symbolic link");
                                return FileVisitResult.CONTINUE;
                            }
                            throw exception;
                        }
                    });
        } catch (IOException | UncheckedIOException e) {
            throw new SkillBuildException("skill directory " + root + " cannot be read", e);
        }

        if (!problems.isEmpty()) {
            throw new SkillValidationException(problems);
        }
        return List.copyOf(files.values());
    }

    private static String relativePath(Path root, Path path) {
        StringBuilder relative = new StringBuilder();
        for (Path segment : root.relativize(path)) {
            if (!relative.isEmpty()) {
                relative.append('/');
            }
            relative.append(segment);
        }
        return relative.toString();
    }

    private static void addParentDirectories(Path root, Path directory, Map<String, SkillFile> files) {
        Path current = directory;
        while (current != null && !current.equals(root)) {
            String path = relativePath(root, current) + "/";
            if (files.putIfAbsent(path, SkillFile.directory(path, current)) != null) {
                return;
            }
            current = current.getParent();
        }
    }

    private boolean isIncluded(String path) {
        return includes.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private boolean isExcluded(String path) {
        return excludes.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private boolean isExecutable(String path, Path file) throws IOException {
        return executables.stream().anyMatch(pattern -> pattern.matches(path)) || hasExecutableBit(file);
    }

    /**
     * File systems without posix permissions (windows) never report an executable bit - only the configured
     * patterns apply there.
     */
    private static boolean hasExecutableBit(Path file) throws IOException {
        try {
            return Files.getPosixFilePermissions(file).contains(PosixFilePermission.OWNER_EXECUTE);
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
