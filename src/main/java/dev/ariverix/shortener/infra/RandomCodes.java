package dev.ariverix.shortener.infra;

import java.security.SecureRandom;
import java.util.function.Supplier;

public final class RandomCodes implements Supplier<String> {
    private static final String SYMBOLS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private final SecureRandom random = new SecureRandom();

    @Override
    public String get() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            result.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        }
        return result.toString();
    }
}
