package dev.ariverix.shortener.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ariverix.shortener.Config;
import dev.ariverix.shortener.core.Link;
import dev.ariverix.shortener.core.LinkService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void restoresUserAndLinkAfterRestart() throws Exception {
        Config config = config();
        UUID owner = UUID.randomUUID();
        LinkService before = service(config);
        before.register(owner);
        Link link = before.create(owner, "https://example.org", 2);
        LinkService after = service(config);
        assertTrue(after.known(owner));
        assertEquals(link.url(), after.info(owner, link.code()).url());
    }

    @Test
    void restoresClickCounterAfterRestart() throws Exception {
        Config config = config();
        UUID owner = UUID.randomUUID();
        LinkService before = service(config);
        before.register(owner);
        Link link = before.create(owner, "https://example.org", 2);
        before.open(link.code(), uri -> { });
        LinkService after = service(config);
        assertEquals(1, after.info(owner, link.code()).visits());
    }

    @Test
    void rejectsCorruptDataFile() throws Exception {
        Path target = directory.resolve("links.properties");
        Files.writeString(target, "link.ABCDEFGHIJ=broken");
        assertThrows(IOException.class, () -> new FileRepository(target).load());
    }

    @Test
    void restoresNotificationsAfterRestart() throws Exception {
        Config config = config();
        UUID owner = UUID.randomUUID();
        LinkService before = service(config);
        before.register(owner);
        Link link = before.create(owner, "https://example.org", 1);
        before.open(link.code(), uri -> { });
        LinkService after = service(config);
        assertEquals(1, after.notices(owner).size());
    }

    private Config config() {
        return new Config(Duration.ofMinutes(10), 2, 50, 1,
                directory.resolve("links.properties"), directory.resolve("session.txt"),
                "short://");
    }

    private LinkService service(Config config) throws Exception {
        return new LinkService(new FileRepository(config.dataFile()),
                new RandomCodes(), config, Clock.systemUTC(), ignored -> { });
    }
}
