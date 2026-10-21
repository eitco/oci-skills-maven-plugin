package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.OciRegistryClient;
import de.eitco.cicd.ociskills.core.registry.PushResult;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;
import land.oras.ContainerRef;
import land.oras.Manifest;
import land.oras.Registry;
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
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pushes skill images using the ORAS java SDK. All ORAS specific code is confined to this module.
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
