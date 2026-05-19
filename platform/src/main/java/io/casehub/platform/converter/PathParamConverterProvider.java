package io.casehub.platform.converter;

import io.casehub.platform.api.path.Path;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * JAX-RS {@link ParamConverterProvider} that registers {@link PathParamConverter}
 * for casehub {@link Path} parameters. Activated automatically via Jandex discovery
 * when {@code casehub-platform} is on the classpath.
 *
 * <p>Allows REST endpoints to declare {@code @PathParam} and {@code @QueryParam}
 * of type {@link Path} without manual string conversion:
 * <pre>
 *   {@literal @}GET {@literal @}Path("/prefs/{scope}")
 *   public Response getPrefs({@literal @}PathParam("scope") Path scope) { ... }
 * </pre>
 */
@Provider
public class PathParamConverterProvider implements ParamConverterProvider {

    private static final PathParamConverter INSTANCE = new PathParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType,
                                               Type genericType,
                                               Annotation[] annotations) {
        if (Path.class.equals(rawType)) {
            return (ParamConverter<T>) INSTANCE;
        }
        return null;
    }
}
