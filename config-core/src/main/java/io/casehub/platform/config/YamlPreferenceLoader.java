package io.casehub.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.api.path.Path;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlPreferenceLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private YamlPreferenceLoader() {}

    @SuppressWarnings("unchecked")
    public static Map<Path, Map<String, String>> load(InputStream is) {
        Map<Path, Map<String, String>> result = new HashMap<>();
        if (is == null) {return result;}

        Map<String, Object> doc;
        try {
            doc = MAPPER.readValue(is, Map.class);
        } catch (MismatchedInputException e) {
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (doc == null) {return result;}

        List<Map<String, Object>> entries = (List<Map<String, Object>>) doc.get("entries");
        if (entries == null) {return result;}

        for (Map<String, Object> entry : entries) {
            String scopeStr = (String) entry.get("scope");
            Path   scopeKey = scopeStr != null ? Path.parse(scopeStr) : null;

            Map<String, String> prefs = result.computeIfAbsent(scopeKey, k -> new HashMap<>());
            entry.forEach((k, v) -> {
                if (!"scope".equals(k)) {prefs.put(k, String.valueOf(v));}
            });
        }
        return result;
    }
}
