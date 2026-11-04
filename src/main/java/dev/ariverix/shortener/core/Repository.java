package dev.ariverix.shortener.core;

import java.io.IOException;

public interface Repository {
    State load() throws IOException;

    void save(State state) throws IOException;
}
