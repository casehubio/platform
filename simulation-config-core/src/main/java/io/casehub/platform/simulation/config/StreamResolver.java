package io.casehub.platform.simulation.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StreamResolver {

    private StreamResolver() {}

    public static InputStream openStream(String path) {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resource);
            if (is == null) {
                throw new IllegalArgumentException("File not found on classpath: " + resource);
            }
            return is;
        }
        try {
            return Files.newInputStream(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open file: " + path, e);
        }
    }
}
