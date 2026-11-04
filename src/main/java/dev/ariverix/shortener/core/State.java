package dev.ariverix.shortener.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record State(Map<String, Link> links, Set<UUID> users, List<Notice> notices) {
    public static State empty() {
        return new State(new HashMap<>(), new HashSet<>(), new ArrayList<>());
    }

    public State copy() {
        return new State(new HashMap<>(links), new HashSet<>(users), new ArrayList<>(notices));
    }
}
