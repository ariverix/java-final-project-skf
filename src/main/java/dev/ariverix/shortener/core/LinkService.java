package dev.ariverix.shortener.core;

import dev.ariverix.shortener.Config;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LinkService {
    private final Repository repository;
    private final Supplier<String> codes;
    private final Config config;
    private final Clock clock;
    private final Consumer<String> events;
    private State state;

    public LinkService(Repository repository, Supplier<String> codes, Config config,
                       Clock clock, Consumer<String> events) throws IOException {
        this.repository = repository;
        this.codes = codes;
        this.config = config;
        this.clock = clock;
        this.events = events;
        this.state = repository.load();
        cleanup();
    }

    public synchronized void register(UUID user) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("UUID не указан");
        }
        if (!state.users().contains(user)) {
            State next = state.copy();
            next.users().add(user);
            commit(next);
        }
    }

    public synchronized boolean known(UUID user) {
        return user != null && state.users().contains(user);
    }

    public synchronized Link create(UUID user, String url, int limit) throws IOException {
        checkUser(user);
        checkLimit(limit);
        String normalized = UrlValidator.validate(url);
        cleanup();
        String code = nextCode();
        long now = clock.millis();
        Link link = new Link(code, normalized, user, now,
                Math.addExact(now, config.ttl().toMillis()), limit, 0);
        State next = state.copy();
        next.links().put(code, link);
        commit(next);
        return link;
    }

    public synchronized Link open(String shortCode, Browser browser) throws IOException {
        String code = normalize(shortCode);
        cleanup();
        Link link = find(code);
        if (link.exhausted()) {
            throw new IllegalStateException("Лимит переходов исчерпан");
        }
        browser.open(URI.create(link.url()));
        Link changed = link.withVisits(link.visits() + 1);
        State next = state.copy();
        next.links().put(code, changed);
        if (changed.exhausted()) {
            addNotice(next, link.owner(), code, "Лимит переходов исчерпан");
        }
        commit(next);
        if (changed.exhausted()) {
            events.accept("Ссылка " + code + ": лимит переходов исчерпан");
        }
        return changed;
    }

    public synchronized List<Link> list(UUID owner) throws IOException {
        checkUser(owner);
        cleanup();
        return state.links().values().stream()
                .filter(link -> link.owner().equals(owner))
                .sorted(Comparator.comparingLong(Link::createdAt)).toList();
    }

    public synchronized Link info(UUID owner, String shortCode) throws IOException {
        checkUser(owner);
        cleanup();
        Link link = find(normalize(shortCode));
        checkOwner(owner, link);
        return link;
    }

    public synchronized Link setLimit(UUID owner, String code, int limit) throws IOException {
        checkLimit(limit);
        Link previous = info(owner, code);
        Link result = previous.withLimit(limit);
        State next = state.copy();
        next.links().put(previous.code(), result);
        commit(next);
        return result;
    }

    public synchronized Link setUrl(UUID owner, String code, String url) throws IOException {
        String normalized = UrlValidator.validate(url);
        Link previous = info(owner, code);
        Link result = previous.withUrl(normalized);
        State next = state.copy();
        next.links().put(previous.code(), result);
        commit(next);
        return result;
    }

    public synchronized void delete(UUID owner, String code) throws IOException {
        Link previous = info(owner, code);
        State next = state.copy();
        next.links().remove(previous.code());
        commit(next);
    }

    public synchronized List<Notice> notices(UUID user) {
        checkUser(user);
        return state.notices().stream().filter(n -> n.owner().equals(user)).toList();
    }

    public synchronized int cleanup() throws IOException {
        long now = clock.millis();
        List<Link> expired = state.links().values().stream()
                .filter(link -> link.expired(now)).toList();
        if (expired.isEmpty()) {
            return 0;
        }
        State next = state.copy();
        for (Link link : expired) {
            next.links().remove(link.code());
            addNotice(next, link.owner(), link.code(), "Срок действия ссылки истёк");
        }
        commit(next);
        for (Link link : expired) {
            events.accept("Ссылка " + link.code() + ": срок действия истёк");
        }
        return expired.size();
    }

    public String shortUrl(String code) {
        return config.prefix() + code;
    }

    private String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Укажите код ссылки");
        }
        String code = value.startsWith(config.prefix())
                ? value.substring(config.prefix().length()) : value;
        if (!code.matches("[A-Za-z0-9]{8,16}")) {
            throw new IllegalArgumentException("Некорректный короткий код");
        }
        return code;
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = codes.get();
            if (candidate != null && candidate.matches("[A-Za-z0-9]{8,16}")
                    && !state.links().containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Не удалось создать уникальный код");
    }

    private Link find(String code) {
        Link link = state.links().get(code);
        if (link == null) {
            throw new IllegalArgumentException("Ссылка не найдена или срок действия истёк");
        }
        return link;
    }

    private void checkOwner(UUID user, Link link) {
        if (!link.owner().equals(user)) {
            throw new SecurityException("Изменять ссылку может только её владелец");
        }
    }

    private void checkUser(UUID user) {
        if (user == null || !state.users().contains(user)) {
            throw new SecurityException("Неизвестный пользователь");
        }
    }

    private void checkLimit(int limit) {
        if (limit < 1 || limit > config.maxLimit()) {
            throw new IllegalArgumentException("Лимит должен быть от 1 до " + config.maxLimit());
        }
    }

    private void addNotice(State next, UUID owner, String code, String message) {
        next.notices().add(new Notice(owner, code, message, clock.millis()));
    }

    private void commit(State next) throws IOException {
        repository.save(next);
        state = next;
    }
}
