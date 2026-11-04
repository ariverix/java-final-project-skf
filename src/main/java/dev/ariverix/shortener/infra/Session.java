package dev.ariverix.shortener.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class Session {
    private final Path path;

    public Session(Path path) {
        this.path = path;
    }

    public UUID load() throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return UUID.fromString(Files.readString(path).trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("Недопустимый UUID в файле сессии", e);
        }
    }

    public void save(UUID user) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, user.toString(), StandardCharsets.UTF_8);
    }
}
