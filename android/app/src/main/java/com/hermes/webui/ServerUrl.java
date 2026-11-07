package com.hermes.webui;

import java.net.URI;
import java.util.Locale;

/** Single authority for setup, navigation and device-permission origin checks. */
final class ServerUrl {
    static String normalize(String input) {
        URI uri = URI.create(input.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Enter an HTTPS server URL without credentials, query or fragment.");
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!path.endsWith("/")) path += "/";
        return "https://" + uri.getRawAuthority().toLowerCase(Locale.ROOT) + path;
    }

    static boolean sameOrigin(String server, String target) {
        try {
            URI a = URI.create(server), b = URI.create(target);
            return "https".equalsIgnoreCase(b.getScheme()) && b.getRawUserInfo() == null
                    && a.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost())
                    && port(a) == port(b);
        } catch (RuntimeException e) { return false; }
    }
    private static int port(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }
}
