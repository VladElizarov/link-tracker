package ru.tbank.bot.util;

import java.net.URI;

public final class LinkValidator {

    private LinkValidator() {
    }

    public static boolean isValid(String link) {
        if (link == null || link.isBlank() || link.contains(" ")) {
            return false;
        }
        try {
            URI uri = URI.create(link.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            return "github.com".equals(host) || "www.github.com".equals(host)
                || "stackoverflow.com".equals(host) || "www.stackoverflow.com".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
