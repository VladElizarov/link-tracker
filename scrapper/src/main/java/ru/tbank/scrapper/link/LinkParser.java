package ru.tbank.scrapper.link;

import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class LinkParser {

    public ParsedLink parse(String url) {
        if (url == null) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || !("https".equals(scheme) || "http".equals(scheme))) {
            return null;
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.replaceAll("^/+", "").split("/+");

        if (host.equals("github.com") || host.equals("www.github.com")) {
            if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                return new GitHubLink(parts[0], parts[1]);
            }
            return null;
        }

        if (host.equals("stackoverflow.com") || host.equals("www.stackoverflow.com")) {
            if (parts.length >= 2 && parts[0].equals("questions")) {
                try {
                    return new StackOverflowLink(Long.parseLong(parts[1]));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }

        return null;
    }
}
