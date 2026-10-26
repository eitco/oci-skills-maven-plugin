package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.layout.OciLayoutWriter;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;
import de.eitco.cicd.ociskills.core.oci.OciJson;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.OciRegistryClient;
import de.eitco.cicd.ociskills.core.registry.PushResult;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import land.oras.ContainerRef;
import land.oras.Descriptor;
import land.oras.Manifest;
import land.oras.Registry;
import land.oras.Tags;
import land.oras.auth.NoAuthProvider;
import land.oras.auth.UsernamePasswordProvider;
import land.oras.exception.OrasException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pushes and pulls skill images using the ORAS java SDK. All ORAS specific code is confined to this module.
 * <p>
 * Push order: check and upload the config blob, check and upload the layer blobs, push the manifest under the
 * destination tag, read the digest the registry reports for the tag.
 */
public final class OrasRegistryClient implements OciRegistryClient {

    private static final Set<String> MEDIA_TYPE_ERROR_CODES = Set.of("MANIFEST_INVALID", "UNSUPPORTED",
            "MANIFEST_UNVERIFIED", "SIZE_INVALID");

    private final RegistryConnection connection;

    public OrasRegistryClient(RegistryConnection connection) {
        this.connection = connection;
    }

    @Override
    public PushResult push(OciImageLayout image, OciReference destination, RegistryCredentials credentials)
            throws SkillPushException {
        Registry registry = registry(credentials);
        ContainerRef reference = ContainerRef.parse(destination.toString());

        List<OciDescriptor> uploaded = new ArrayList<>();
        List<OciDescriptor> skipped = new ArrayList<>();
        List<OciDescriptor> blobs = new ArrayList<>();
        blobs.add(image.manifest().config());
        blobs.addAll(image.manifest().layers());
        for (OciDescriptor blob : blobs) {
            if (exists(registry, reference, blob, destination)) {
                skipped.add(blob);
            } else {
                upload(registry, reference, image, blob, destination);
                uploaded.add(blob);
            }
        }

        OciDescriptor manifestDescriptor = image.manifestDescriptor();
        String confirmedDigest;
        try {
            String json = new String(Files.readAllBytes(image.blob(manifestDescriptor)), StandardCharsets.UTF_8);
            Manifest pushed = registry.pushManifest(reference, Manifest.fromJson(json));
            confirmedDigest = pushed.getDescriptor() == null ? null : pushed.getDescriptor().getDigest();
            if (confirmedDigest == null) {
                confirmedDigest = registry.probeDescriptor(reference).getDigest();
            }
        } catch (IOException e) {
            throw new SkillPushException(SkillPushException.Reason.INVALID_LAYOUT, destination, manifestDescriptor,
                    "manifest cannot be read: " + e, e);
        } catch (RuntimeException e) {
            throw failure(e, destination, manifestDescriptor, true);
        }
        return new PushResult(destination, confirmedDigest == null ? null : Digest.parse(confirmedDigest),
                List.copyOf(uploaded), List.copyOf(skipped));
    }

    @Override
    public List<String> tags(OciReference repository, RegistryCredentials credentials) throws SkillPullException {
        String name = repository.registry() + "/" + repository.repository();
        Registry registry = pullRegistry(credentials);
        try {
            Tags tags = registry.getTags(ContainerRef.parse(name));
            return tags.tags() == null ? List.of() : List.copyOf(tags.tags());
        } catch (OrasException e) {
            if (e.getStatusCode() == 404) {
                return List.of();
            }
            throw pullFailure(e, "listing the tags of " + name);
        } catch (RuntimeException e) {
            throw pullFailure(e, "listing the tags of " + name);
        }
    }

    /**
     * Pull order: fetch the manifest and verify it against the requested digest, fetch the config and layer blobs,
     * write the index and verify the complete layout.
     */
    @Override
    public Optional<OciImageLayout> pull(OciReference source, Digest digest, Path directory,
                                         RegistryCredentials credentials) throws SkillPullException {
        Registry registry = pullRegistry(credentials);
        ContainerRef reference = ContainerRef.parse(source.toString());
        if (digest != null) {
            reference = reference.withDigest(digest.toString());
        }
        String name = digest == null ? source.toString() : source + "@" + digest;
        Descriptor descriptor;
        try {
            descriptor = registry.getDescriptor(reference);
        } catch (OrasException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw pullFailure(e, "fetching the manifest of " + name);
        } catch (RuntimeException e) {
            throw pullFailure(e, "fetching the manifest of " + name);
        }
        byte[] manifestBytes = descriptor.getJson().getBytes(StandardCharsets.UTF_8);
        Digest manifestDigest = Digest.sha256(OciJson.sha256().digest(manifestBytes));
        if (digest != null && !digest.equals(manifestDigest)) {
            throw new SkillPullException("the manifest of " + name + " does not match its digest (actual "
                    + manifestDigest + ")");
        }
        String mediaType = descriptor.getMediaType() == null ? null : descriptor.getMediaType().split(";")[0].trim();
        if (!MediaTypes.IMAGE_MANIFEST.equals(mediaType)) {
            throw new SkillPullException(name + " is no skill image - unsupported media type " + mediaType);
        }
        try {
            OciImageManifest manifest = OciJson.deserialize(manifestBytes, OciImageManifest.class);
            if (manifest.config() == null || manifest.layers() == null) {
                throw new SkillPullException(name + " is no skill image - the manifest has no config or layers");
            }
            OciLayoutWriter writer = new OciLayoutWriter(directory);
            writer.prepare();
            writer.writeBlob(manifestBytes, manifestDigest);
            List<OciDescriptor> blobs = new ArrayList<>();
            blobs.add(manifest.config());
            blobs.addAll(manifest.layers());
            for (OciDescriptor blob : blobs) {
                Path temporary = writer.newTemporaryBlobFile();
                try {
                    registry.fetchBlob(reference.withDigest(blob.digest().toString()), temporary);
                } catch (RuntimeException e) {
                    throw pullFailure(e, "fetching blob " + blob.mediaType() + " " + blob.digest() + " of " + name);
                }
                writer.moveBlob(temporary, blob.digest());
            }
            writer.writeIndex(new OciDescriptor(MediaTypes.IMAGE_MANIFEST, manifestDigest, manifestBytes.length),
                    source.tag());
            return Optional.of(new OciLayoutReader().read(directory));
        } catch (SkillBuildException e) {
            throw new SkillPullException("pulling " + name + " failed: " + e.getMessage(), e);
        }
    }

    private Registry pullRegistry(RegistryCredentials credentials) throws SkillPullException {
        try {
            return registry(credentials);
        } catch (SkillPushException e) {
            throw new SkillPullException(e.getMessage(), e);
        }
    }

    private static SkillPullException pullFailure(RuntimeException exception, String action) {
        SkillPushException.Reason reason = reason(exception, false);
        String description = switch (reason) {
            case PERMISSION_DENIED -> "no permission to pull";
            case REPOSITORY_NOT_FOUND, MEDIA_TYPE_REJECTED, UNKNOWN -> "pull failed";
            default -> reason.description();
        };
        return new SkillPullException(description + " - " + action + ": " + detail(exception), exception);
    }

    private Registry registry(RegistryCredentials credentials) throws SkillPushException {
        Registry.Builder builder = Registry.builder()
                .withInsecure(connection.allowPlainHttp())
                .withAuthProvider(credentials.isAnonymous()
                        ? new NoAuthProvider()
                        : new UsernamePasswordProvider(credentials.username(),
                        credentials.password() == null ? "" : credentials.password()));
        String pemBundle = TrustMaterial.pemBundle(connection);
        if (pemBundle != null) {
            builder.withCaContent(pemBundle);
        }
        return builder.build();
    }

    private static boolean exists(Registry registry, ContainerRef reference, OciDescriptor blob,
                                  OciReference destination) throws SkillPushException {
        try {
            registry.fetchBlobDescriptor(reference.withDigest(blob.digest().toString()));
            return true;
        } catch (OrasException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw failure(e, destination, blob, false);
        } catch (RuntimeException e) {
            throw failure(e, destination, blob, false);
        }
    }

    private static void upload(Registry registry, ContainerRef reference, OciImageLayout image, OciDescriptor blob,
                               OciReference destination) throws SkillPushException {
        try {
            registry.pushBlob(reference, image.blob(blob), Map.of());
        } catch (RuntimeException e) {
            throw failure(e, destination, blob, false);
        }
    }

    private static SkillPushException failure(RuntimeException exception, OciReference destination,
                                              OciDescriptor descriptor, boolean manifest) {
        SkillPushException.Reason reason = reason(exception, manifest);
        return new SkillPushException(reason, destination, descriptor, detail(exception), exception);
    }

    private static SkillPushException.Reason reason(RuntimeException exception, boolean manifest) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof CertificateException || cause instanceof CertPathBuilderException
                    || cause instanceof CertPathValidatorException
                    || cause.getClass().getName().endsWith("ValidatorException")) {
                return SkillPushException.Reason.TLS_UNTRUSTED;
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException || cause instanceof HttpConnectTimeoutException) {
                return SkillPushException.Reason.REGISTRY_UNREACHABLE;
            }
        }
        if (exception instanceof OrasException orasException) {
            String code = orasException.getError() == null ? null : orasException.getError().code();
            return switch (orasException.getStatusCode()) {
                case 401 -> SkillPushException.Reason.AUTHENTICATION_FAILED;
                case 403 -> SkillPushException.Reason.PERMISSION_DENIED;
                case 404 -> SkillPushException.Reason.REPOSITORY_NOT_FOUND;
                case 415 -> SkillPushException.Reason.MEDIA_TYPE_REJECTED;
                case 400 -> manifest || (code != null && MEDIA_TYPE_ERROR_CODES.contains(code))
                        ? SkillPushException.Reason.MEDIA_TYPE_REJECTED
                        : SkillPushException.Reason.UNKNOWN;
                default -> SkillPushException.Reason.UNKNOWN;
            };
        }
        return SkillPushException.Reason.UNKNOWN;
    }

    private static String detail(RuntimeException exception) {
        StringBuilder detail = new StringBuilder(String.valueOf(exception.getMessage()));
        if (exception instanceof OrasException orasException && orasException.getError() != null) {
            detail.append(" (").append(orasException.getError().code()).append(": ")
                    .append(orasException.getError().message()).append(')');
        }
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != exception) {
            detail.append(" - caused by ").append(root);
        }
        return detail.toString();
    }
}
