package dev.ariverix.shortener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public record Config(Duration ttl, int defaultLimit, int maxLimit, int cleanupSeconds,
                     Path dataFile, Path sessionFile, String prefix) {
    public Config {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || defaultLimit < 1
                || maxLimit < defaultLimit || cleanupSeconds < 1 || dataFile == null
                || sessionFile == null || prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Недопустимые настройки приложения");
        }
    }

    public static Config load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IOException("Не найден application.properties");
            }
            p.load(in);
        }
        Path local = Path.of("application.properties");
        if (Files.isRegularFile(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                p.load(in);
            }
        }
        try {
            return new Config(Duration.ofSeconds(Long.parseLong(p.getProperty("ttl.seconds"))),
                    Integer.parseInt(p.getProperty("default.limit")),
                    Integer.parseInt(p.getProperty("max.limit")),
                    Integer.parseInt(p.getProperty("cleanup.seconds")),
                    Path.of(p.getProperty("data.file")), Path.of(p.getProperty("session.file")),
                    p.getProperty("short.prefix"));
        } catch (RuntimeException ex) {
            throw new IOException("Ошибка в конфигурации", ex);
        }
    }
}
