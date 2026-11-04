package dev.ariverix.shortener.core;

import java.util.UUID;

public record Notice(UUID owner, String code, String text, long time) {
}
