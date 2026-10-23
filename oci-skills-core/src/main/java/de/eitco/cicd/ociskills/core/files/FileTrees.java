package de.eitco.cicd.ociskills.core.files;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Helpers for whole directory trees. Symbolic links are never followed.
 */
public final class FileTrees {

    private FileTrees() {
    }

    /**
     * Deletes the given file or directory with all its content. Does nothing if it does not exist.
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copies the content of {@code source} into {@code target}, keeping the executable bit of files.
     */
    public static void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path copy = target.resolve(source.relativize(file).toString());
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Sets the permissions to {@code rwxr-xr-x} or {@code rw-r--r--}. Does nothing on file systems without posix
     * permissions.
     */
    public static void setExecutable(Path file, boolean executable) throws IOException {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(executable ? "rwxr-xr-x" : "rw-r--r--"));
        } catch (UnsupportedOperationException e) {
            if (!file.toFile().setExecutable(executable)) {
                throw new IOException("cannot change the permissions of " + file);
            }
        }
    }
}
