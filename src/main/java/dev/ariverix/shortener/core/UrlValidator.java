package dev.ariverix.shortener.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UrlValidator {
    private UrlValidator() {
    }

    public static String validate(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new IllegalArgumentException("Длина URL должна быть от 1 до 2048 символов");
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.toLowerCase(Locale.ROOT).equals("https")
                    && !scheme.toLowerCase(Locale.ROOT).equals("http"))
                    || uri.getHost() == null || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Требуется ссылка http:// или https://");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Некорректный URL", e);
        }
    }
}
