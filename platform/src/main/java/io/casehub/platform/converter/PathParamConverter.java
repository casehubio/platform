package io.casehub.platform.converter;

import io.casehub.platform.api.path.Path;
import jakarta.ws.rs.ext.ParamConverter;

/**
 * JAX-RS {@link ParamConverter} for casehub {@link Path}.
 * Converts between string representations and {@link Path} instances using
 * the platform-configured default parser ({@link Path#parse(String)}).
 *
 * <p>Registered automatically via {@link PathParamConverterProvider}.
 */
public class PathParamConverter implements ParamConverter<Path> {

    @Override
    public Path fromString(String value) {
        if (value == null) return null;
        return Path.parse(value);
    }

    @Override
    public String toString(Path value) {
        if (value == null) return null;
        return value.value();
    }
}
