package de.samply.samplexchange.configuration;

import de.samply.samplexchange.SampleXChangeException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Rules for the configured FHIR server URLs and for links the servers send back. */
public final class FhirServerUrl {

    private FhirServerUrl() {
    }

    /**
     * Fails unless {@code url} is an http or https URL whose path ends in {@code /fhir}, the base
     * path every FHIR server we talk to uses.
     */
    public static void validate(String variable, String url) {
        if (url == null || url.isBlank()) {
            throw new SampleXChangeException(variable + " is not set");
        }
        URI uri = parse(url);
        boolean isHttp = uri != null && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
        if (!isHttp || uri.getHost() == null) {
            throw new SampleXChangeException(
                    "%s=%s is not a valid http or https URL, for example http://blaze:8080/fhir"
                            .formatted(variable, url));
        }
        String path = stripTrailingSlash(uri.getPath() == null ? "" : uri.getPath());
        if (!path.toLowerCase(Locale.ROOT).endsWith("/fhir")) {
            throw new SampleXChangeException(
                    "%s=%s must end with /fhir, for example %s://%s/fhir"
                            .formatted(variable, url, uri.getScheme(), uri.getAuthority()));
        }
    }

    /**
     * Whether {@code link} points at the server whose base URL is {@code serverBase}: same scheme,
     * host and port, and a path at or below the base path.
     */
    public static boolean isOnServer(String serverBase, String link) {
        URI base = parse(serverBase);
        URI target = parse(link);
        if (base == null || target == null || base.getHost() == null || target.getHost() == null) {
            return false;
        }
        String basePath = stripTrailingSlash(base.getPath() == null ? "" : base.getPath());
        String linkPath = target.getPath() == null ? "" : target.getPath();
        return base.getScheme().equalsIgnoreCase(target.getScheme())
                && base.getHost().equalsIgnoreCase(target.getHost())
                && effectivePort(base) == effectivePort(target)
                && (linkPath.equals(basePath) || linkPath.startsWith(basePath + "/"));
    }

    private static URI parse(String url) {
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
