package dev.ariverix.shortener.infra;

import dev.ariverix.shortener.core.Link;
import dev.ariverix.shortener.core.Notice;
import dev.ariverix.shortener.core.Repository;
import dev.ariverix.shortener.core.State;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;

public final class FileRepository implements Repository {
    private final Path file;

    public FileRepository(Path file) {
        this.file = file.toAbsolutePath();
    }

    @Override
    public synchronized State load() throws IOException {
        if (!Files.exists(file)) {
            return State.empty();
        }
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            props.load(input);
        }
        try {
            State state = State.empty();
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                if (key.startsWith("user.")) {
                    state.users().add(UUID.fromString(key.substring(5)));
                } else if (key.startsWith("link.")) {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 6) {
                        throw new IllegalArgumentException("Неверная запись ссылки");
                    }
                    String code = key.substring(5);
                    state.links().put(code, new Link(code, decode(parts[1]),
                            UUID.fromString(parts[0]), Long.parseLong(parts[2]),
                            Long.parseLong(parts[3]), Integer.parseInt(parts[4]),
                            Integer.parseInt(parts[5])));
                } else if (key.startsWith("notice.")) {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 4) {
                        throw new IllegalArgumentException("Неверная запись уведомления");
                    }
                    state.notices().add(new Notice(UUID.fromString(parts[0]),
                            parts[1], decode(parts[2]), Long.parseLong(parts[3])));
                }
            }
            state.notices().sort(Comparator.comparingLong(Notice::time));
            return state;
        } catch (RuntimeException ex) {
            throw new IOException("Повреждён файл данных: " + file, ex);
        }
    }

    @Override
    public synchronized void save(State state) throws IOException {
        Properties props = new Properties();
        for (UUID user : state.users()) {
            props.setProperty("user." + user, "1");
        }
        for (Link link : state.links().values()) {
            props.setProperty("link." + link.code(), String.join("|", link.owner().toString(),
                    encode(link.url()), Long.toString(link.createdAt()),
                    Long.toString(link.expiresAt()), Integer.toString(link.limit()),
                    Integer.toString(link.visits())));
        }
        for (int i = 0; i < state.notices().size(); i++) {
            Notice notice = state.notices().get(i);
            props.setProperty("notice." + i, String.join("|", notice.owner().toString(),
                    notice.code(), encode(notice.text()), Long.toString(notice.time())));
        }
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path tmp = Files.createTempFile(directory, "links-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(tmp)) {
                props.store(output, "Link shortener data");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
