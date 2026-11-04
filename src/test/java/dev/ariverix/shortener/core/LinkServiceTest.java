package dev.ariverix.shortener.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ariverix.shortener.Config;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkServiceTest {
    private static final String URL = "https://example.org/page";
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final MovingClock clock = new MovingClock();
    private final MemoryRepository repository = new MemoryRepository();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger opened = new AtomicInteger();
    private final java.util.List<String> messages = new java.util.ArrayList<>();
    private Config config;
    private LinkService service;

    @BeforeEach
    void setUp() throws Exception {
        config = new Config(Duration.ofSeconds(60), 2, 10, 1,
                java.nio.file.Path.of("data/unused"), java.nio.file.Path.of("data/user"),
                "short://");
        service = new LinkService(repository, codeGenerator(), config, clock, messages::add);
        service.register(first);
        service.register(second);
    }

    private Supplier<String> codeGenerator() {
        return () -> String.format("%010d", sequence.incrementAndGet());
    }

    private Browser browser() {
        return uri -> opened.incrementAndGet();
    }

    @Test
    void createsUniqueCodesForSameUser() throws Exception {
        assertNotEquals(service.create(first, URL, 2).code(),
                service.create(first, URL, 2).code());
    }

    @Test
    void createsUniqueCodesForDifferentUsers() throws Exception {
        assertNotEquals(service.create(first, URL, 2).code(),
                service.create(second, URL, 2).code());
    }

    @Test
    void retainsOriginalUrl() throws Exception {
        assertEquals(URL, service.create(first, URL, 2).url());
    }

    @Test
    void rejectsFtpUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(first, "ftp://example.org/file", 2));
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(first, "https:///bad", 2));
    }

    @Test
    void rejectsZeroLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.create(first, URL, 0));
    }

    @Test
    void rejectsLimitAboveConfiguredMaximum() {
        assertThrows(IllegalArgumentException.class, () -> service.create(first, URL, 11));
    }

    @Test
    void tracksSuccessfulOpens() throws Exception {
        Link link = service.create(first, URL, 2);
        assertEquals(1, service.open(link.code(), browser()).visits());
        assertEquals(1, opened.get());
    }

    @Test
    void allowsLastPermittedOpen() throws Exception {
        Link link = service.create(first, URL, 2);
        service.open(link.code(), browser());
        assertEquals(2, service.open(link.code(), browser()).visits());
    }

    @Test
    void blocksOpenAfterLimit() throws Exception {
        Link link = service.create(first, URL, 1);
        service.open(link.code(), browser());
        assertThrows(IllegalStateException.class,
                () -> service.open(link.code(), browser()));
        assertEquals(1, opened.get());
    }

    @Test
    void browserErrorDoesNotSpendAttempt() throws Exception {
        Link link = service.create(first, URL, 1);
        assertThrows(IOException.class, () -> service.open(link.code(), uri -> {
            throw new IOException("Browser error");
        }));
        assertEquals(0, service.info(first, link.code()).visits());
    }

    @Test
    void linkRemainsActiveBeforeTtl() throws Exception {
        Link link = service.create(first, URL, 2);
        clock.advance(59);
        assertEquals(1, service.list(first).size());
        assertEquals(link.code(), service.list(first).get(0).code());
    }

    @Test
    void expiresAtExactTtlBoundary() throws Exception {
        Link link = service.create(first, URL, 2);
        clock.advance(60);
        assertThrows(IllegalArgumentException.class,
                () -> service.open(link.code(), browser()));
        assertEquals(0, opened.get());
    }

    @Test
    void cleanupDeletesExpiredLinks() throws Exception {
        service.create(first, URL, 2);
        clock.advance(61);
        assertEquals(1, service.cleanup());
        assertTrue(service.list(first).isEmpty());
    }

    @Test
    void cleanupProducesOwnerNotification() throws Exception {
        Link link = service.create(first, URL, 2);
        clock.advance(61);
        service.cleanup();
        assertEquals(link.code(), service.notices(first).get(0).code());
        assertTrue(service.notices(first).get(0).text().contains("истёк"));
    }

    @Test
    void onlyOwnerSeesLinkInList() throws Exception {
        service.create(first, URL, 2);
        assertTrue(service.list(second).isEmpty());
    }

    @Test
    void preventsForeignLimitEdit() throws Exception {
        Link link = service.create(first, URL, 2);
        assertThrows(SecurityException.class,
                () -> service.setLimit(second, link.code(), 3));
    }

    @Test
    void preventsForeignUrlEdit() throws Exception {
        Link link = service.create(first, URL, 2);
        assertThrows(SecurityException.class,
                () -> service.setUrl(second, link.code(), "https://example.com"));
    }

    @Test
    void preventsForeignDelete() throws Exception {
        Link link = service.create(first, URL, 2);
        assertThrows(SecurityException.class,
                () -> service.delete(second, link.code()));
    }

    @Test
    void ownerCanEditLimit() throws Exception {
        Link link = service.create(first, URL, 2);
        assertEquals(5, service.setLimit(first, link.code(), 5).limit());
    }

    @Test
    void ownerCanEditDestination() throws Exception {
        Link link = service.create(first, URL, 2);
        assertEquals("https://example.com/new",
                service.setUrl(first, link.code(), "https://example.com/new").url());
    }

    @Test
    void editedDestinationMustBeValid() throws Exception {
        Link link = service.create(first, URL, 2);
        assertThrows(IllegalArgumentException.class,
                () -> service.setUrl(first, link.code(), "javascript:alert(1)"));
    }

    @Test
    void ownerCanDelete() throws Exception {
        Link link = service.create(first, URL, 2);
        service.delete(first, link.code());
        assertTrue(service.list(first).isEmpty());
    }

    @Test
    void canStoreMultipleLinksForOneUser() throws Exception {
        service.create(first, URL, 2);
        service.create(first, "https://example.net/second", 3);
        assertEquals(2, service.list(first).size());
    }

    @Test
    void finalClickCreatesNotification() throws Exception {
        Link link = service.create(first, URL, 1);
        service.open(link.code(), browser());
        assertEquals(1, service.notices(first).size());
        assertTrue(service.notices(first).get(0).text().contains("Лимит"));
    }

    @Test
    void increasingExhaustedLimitReactivates() throws Exception {
        Link link = service.create(first, URL, 1);
        service.open(link.code(), browser());
        service.setLimit(first, link.code(), 2);
        assertEquals(2, service.open(link.code(), browser()).visits());
    }

    @Test
    void unregisteredUserCannotCreate() {
        assertThrows(SecurityException.class,
                () -> service.create(UUID.randomUUID(), URL, 2));
    }

    @Test
    void acceptsShortUrlInOpenCommand() throws Exception {
        Link link = service.create(first, URL, 2);
        service.open(service.shortUrl(link.code()), browser());
        assertEquals(1, opened.get());
    }

    @Test
    void retriesGeneratedCodeCollision() throws Exception {
        Supplier<String> colliding = new Supplier<>() {
            private int calls;

            @Override
            public String get() {
                calls++;
                return calls <= 2 ? "AAAAAAAAAA" : "BBBBBBBBBB";
            }
        };
        LinkService local = new LinkService(new MemoryRepository(), colliding, config,
                clock, ignored -> { });
        local.register(first);
        Link one = local.create(first, URL, 2);
        Link two = local.create(first, URL, 2);
        assertNotEquals(one.code(), two.code());
    }

    @Test
    void doesNotDuplicateExpiredNotices() throws Exception {
        service.create(first, URL, 1);
        clock.advance(61);
        service.cleanup();
        service.cleanup();
        assertEquals(1, service.notices(first).size());
    }

    static final class MemoryRepository implements Repository {
        private State saved = State.empty();

        @Override
        public State load() {
            return saved.copy();
        }

        @Override
        public void save(State state) {
            saved = state.copy();
        }
    }

    static final class MovingClock extends Clock {
        private Instant time = Instant.parse("2026-11-02T09:00:00Z");

        void advance(long seconds) {
            time = time.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return time;
        }
    }
}
