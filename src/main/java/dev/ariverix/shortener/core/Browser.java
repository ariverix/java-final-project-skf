package dev.ariverix.shortener.core;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface Browser {
    void open(URI url) throws IOException;
}
