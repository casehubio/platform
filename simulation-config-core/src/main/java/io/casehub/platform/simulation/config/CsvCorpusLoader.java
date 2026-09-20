package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvCorpusLoader implements CorpusLoader {

    private static final String QN_COLUMN = "_qualified_name";
    private static final String KEY_COLUMN = "_key";
    private static final String TENANCY_COLUMN = "_tenancy_id";

    @Override
    public boolean supports(String path) {
        return path.toLowerCase().endsWith(".csv");
    }

    @Override
    public Map<String, List<InvocationRecord<Object, Object>>> load(
            InputStream input, String defaultTenancyId) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                return Map.of();
            }

            String[] headers = headerLine.split(",");
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }

            int qnIndex = indexOf(headers, QN_COLUMN);
            if (qnIndex < 0) {
                throw new IllegalArgumentException(
                        "CSV corpus file must have a '" + QN_COLUMN + "' column. "
                        + "Found columns: " + String.join(", ", headers));
            }
            int keyIndex = indexOf(headers, KEY_COLUMN);
            int tenancyIndex = indexOf(headers, TENANCY_COLUMN);

            Map<String, List<InvocationRecord<Object, Object>>> result = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] values = line.split(",", -1);
                if (values.length <= qnIndex) continue;
                String qualifiedName = values[qnIndex].trim();
                String key = keyIndex >= 0 ? values[keyIndex].trim() : null;
                String tenancyId = tenancyIndex >= 0 && !values[tenancyIndex].trim().isEmpty()
                        ? values[tenancyIndex].trim() : defaultTenancyId;

                Map<String, Object> outputMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    if (i == qnIndex || i == keyIndex || i == tenancyIndex) continue;
                    outputMap.put(headers[i], values[i].trim());
                }

                result.computeIfAbsent(qualifiedName, k -> new ArrayList<>())
                        .add(new InvocationRecord<>(tenancyId, key, key, outputMap, Instant.now()));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse CSV corpus", e);
        }
    }

    private static int indexOf(String[] arr, String target) {
        for (int i = 0; i < arr.length; i++) {
            if (target.equals(arr[i])) return i;
        }
        return -1;
    }
}
