package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.InvocationRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

public class JsonCorpusLoader implements CorpusLoader {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Override
    public boolean supports(String path) {
        return path.toLowerCase().endsWith(".json");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<InvocationRecord<Object, Object>>> load(
            InputStream input, String defaultTenancyId) {
        try {
            Map<String, List<Map<String, Object>>> raw = JSON_MAPPER.readValue(input, Map.class);
            return CorpusEntryParser.parseCorpusMap(raw, defaultTenancyId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON corpus", e);
        }
    }
}
