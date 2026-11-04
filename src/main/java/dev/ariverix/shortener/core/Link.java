package dev.ariverix.shortener.core;

import java.util.UUID;

public record Link(String code, String url, UUID owner, long createdAt, long expiresAt,
                   int limit, int visits) {
    public boolean expired(long now) {
        return now >= expiresAt;
    }

    public boolean exhausted() {
        return visits >= limit;
    }

    public Link withVisits(int value) {
        return new Link(code, url, owner, createdAt, expiresAt, limit, value);
    }

    public Link withLimit(int value) {
        return new Link(code, url, owner, createdAt, expiresAt, value, visits);
    }

    public Link withUrl(String value) {
        return new Link(code, value, owner, createdAt, expiresAt, limit, visits);
    }
}
