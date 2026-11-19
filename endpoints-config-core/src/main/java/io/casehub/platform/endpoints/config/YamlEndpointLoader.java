package io.casehub.platform.endpoints.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

public final class YamlEndpointLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private YamlEndpointLoader() {}

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> load(InputStream is) {
        if (is == null) {return List.of();}
        Map<String, Object> doc;
        try {
            doc = MAPPER.readValue(is, Map.class);
        } catch (MismatchedInputException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (doc == null) {return List.of();}
        List<Map<String, Object>> entries = (List<Map<String, Object>>) doc.get("endpoints");
        return entries != null ? entries : List.of();
    }
}
