package de.eitco.cicd.ociskills.registry.oras;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Provides the pinned version of skillctl for compatibility tests - downloaded from its GitHub release and verified
 * against the checksum of the release. To update skillctl, change {@link #VERSION} and the checksums from the
 * {@code checksums.txt} of the release.
 */
final class SkillctlDownload {

    static final String VERSION = "0.7.2";

    private static final Map<String, String> SHA256 = Map.of(
            "linux_amd64", "8906ac98877747caf8fc10910884a40f2e287d68303f706440603a81b0cc33d9",
            "linux_arm64", "3143ec2012414b28a6fe4d98bfb099df45ab57d421cefa661dbebebdfe8e0dfb",
            "darwin_amd64", "c7e10d919d7a5b9ae68061429a7170c6250ceba81c42ccc9d3fe264dd90d8230",
            "darwin_arm64", "e8d04cdba4af5052fe9fb3886e7b5bfb043c7a81642bd9b1d40c4a1e60ddb17e");

    /**
     * the base url of the release - can be replaced with the system property {@code skillctl.downloadUrl}, e.g. by a
     * mirror
     */
    private static final String DOWNLOAD_URL = "https://github.com/redhat-et/skillimage/releases/download/v" + VERSION;

    private static final Path DIRECTORY = Path.of("target", "tools", "skillctl-" + VERSION);
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private SkillctlDownload() {
    }

    /**
     * @return the skillctl to test with: the one given by the system property {@code skillctl.executable} or the
     * downloaded release - the calling test is skipped if it is not available
     */
    static synchronized Path executable() {
        String configured = System.getProperty("skillctl.executable");
        if (configured != null && !configured.isBlank()) {
            Path executable = Path.of(configured);
            assumeTrue(Files.isExecutable(executable), "skillctl.executable " + configured + " is not executable");
            return executable;
        }
        Optional<String> platform = platform(System.getProperty("os.name"), System.getProperty("os.arch"));
        assumeTrue(platform.isPresent(), () -> "skillctl " + VERSION + " is not available for "
                + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        Path executable = DIRECTORY.resolve("skillctl").toAbsolutePath();
        if (Files.isExecutable(executable)) {
            return executable;
        }
        String asset = "skillctl_" + VERSION + "_" + platform.get() + ".tar.gz";
        URI archive = URI.create(System.getProperty("skillctl.downloadUrl", DOWNLOAD_URL) + "/" + asset);
        try {
            install(archive, SHA256.get(platform.get()), executable);
        } catch (IOException e) {
            assumeTrue(false, "downloading skillctl " + VERSION + " from " + archive + " failed: " + e);
        }
        return executable;
    }

    /**
     * @return the platform part of the release asset name, e.g. {@code linux_amd64} - empty if not supported
     */
    static Optional<String> platform(String osName, String osArch) {
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String os = name.startsWith("linux") ? "linux" : name.startsWith("mac") ? "darwin" : null;
        String arch = switch (osArch == null ? "" : osArch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> null;
        };
        return os == null || arch == null ? Optional.empty() : Optional.of(os + "_" + arch);
    }

    /**
     * Downloads the archive, verifies its checksum and extracts {@code skillctl} from it. The executable is moved to
     * its place as last step, so it is either complete or missing.
     *
     * @param archive    the url of the {@code tar.gz} archive - {@code https} or {@code file}
     * @param sha256     the expected checksum of the archive, hex encoded
     * @param executable the file to write the executable to
     * @throws IOException           if downloading fails
     * @throws IllegalStateException if the checksum does not match or the archive contains no skillctl
     */
    static void install(URI archive, String sha256, Path executable) throws IOException {
        Path directory = Files.createDirectories(executable.toAbsolutePath().getParent());
        Path download = Files.createTempFile(directory, "skillctl", ".tar.gz.part");
        Path extracted = Files.createTempFile(directory, "skillctl", ".part");
        try {
            download(archive, download);
            String actual = sha256(download);
            if (!actual.equalsIgnoreCase(sha256)) {
                throw new IllegalStateException("the checksum of " + archive + " is " + actual + " instead of "
                        + sha256 + " - the download is not the expected release");
            }
            extract(download, extracted);
            Files.setPosixFilePermissions(extracted, PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.move(extracted, executable, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(download);
            Files.deleteIfExists(extracted);
        }
    }

    private static void download(URI archive, Path target) throws IOException {
        if ("file".equals(archive.getScheme())) {
            Files.copy(Path.of(archive), target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        // the default proxy selector honors -Dhttps.proxyHost and friends
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build();
        HttpRequest request = HttpRequest.newBuilder(archive).timeout(TIMEOUT).GET().build();
        HttpResponse<Path> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading " + archive, e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("downloading " + archive + " returned http status " + response.statusCode());
        }
    }

    private static void extract(Path archive, Path target) throws IOException {
        try (InputStream file = Files.newInputStream(archive);
             TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(new BufferedInputStream(file)))) {
            for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                if (entry.isFile() && entry.getName().equals("skillctl")) {
                    Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IllegalStateException("the archive contains no skillctl");
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
