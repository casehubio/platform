package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CorpusEntryParser {

    private CorpusEntryParser() {}

    public static Map<String, List<InvocationRecord<Object, Object>>> parseCorpusMap(
            Map<String, List<Map<String, Object>>> raw, String defaultTenancyId) {
        if (raw == null) return Map.of();
        var result = new HashMap<String, List<InvocationRecord<Object, Object>>>();
        raw.forEach((qn, entries) -> result.put(qn, toRecords(entries, defaultTenancyId)));
        return result;
    }

    public static List<InvocationRecord<Object, Object>> toRecords(
            List<Map<String, Object>> rawEntries, String defaultTenancyId) {
        var records = new ArrayList<InvocationRecord<Object, Object>>();
        for (var entry : rawEntries) {
            String tenancyId = (String) entry.get("tenancy-id");
            if (tenancyId == null) tenancyId = defaultTenancyId;
            records.add(new InvocationRecord<>(
                    tenancyId, (String) entry.get("key"),
                    entry.get("input"), entry.get("output"), Instant.now()));
        }
        return records;
    }
}
