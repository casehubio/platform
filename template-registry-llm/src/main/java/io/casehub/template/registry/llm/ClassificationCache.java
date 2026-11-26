package io.casehub.template.registry.llm;

import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateMatch;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ClassificationCache {

    public record CacheKey(String namespace, String input) {
        public CacheKey {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(input, "input");
        }
    }

    public record Entry(ClassificationResult.Matched matched, Instant insertedAt) {
        public boolean isPositive() {
            return matched != null;
        }

        public TemplateMatch toTemplateMatch(String namespace) {
            if (matched != null) {
                return new TemplateMatch.Matched(
                    namespace, matched.templateName(), -1,
                    matched.fields(), 0.9);
            }
            return null;
        }
    }

    private final Duration positiveTtl;
    private final Duration negativeTtl;
    private final int maxEntries;
    private final ConcurrentHashMap<CacheKey, Entry> cache = new ConcurrentHashMap<>();

    public ClassificationCache(Duration positiveTtl, Duration negativeTtl, int maxEntries) {
        this.positiveTtl = Objects.requireNonNull(positiveTtl, "positiveTtl");
        this.negativeTtl = Objects.requireNonNull(negativeTtl, "negativeTtl");
        this.maxEntries = maxEntries;
    }

    public Entry get(CacheKey key) {
        Entry entry = cache.get(key);
        if (entry == null) return null;

        Duration ttl = entry.isPositive() ? positiveTtl : negativeTtl;
        if (Instant.now().isAfter(entry.insertedAt().plus(ttl))) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    public void putPositive(CacheKey key, ClassificationResult.Matched matched) {
        evictIfNeeded();
        cache.put(key, new Entry(matched, Instant.now()));
    }

    public void putNegative(CacheKey key) {
        evictIfNeeded();
        cache.put(key, new Entry(null, Instant.now()));
    }

    public void invalidateNamespace(String namespace) {
        cache.keySet().removeIf(k -> k.namespace().equals(namespace));
    }

    private void evictIfNeeded() {
        if (cache.size() >= maxEntries) {
            Instant now = Instant.now();
            cache.entrySet().removeIf(e -> {
                Duration ttl = e.getValue().isPositive() ? positiveTtl : negativeTtl;
                return now.isAfter(e.getValue().insertedAt().plus(ttl));
            });
            if (cache.size() >= maxEntries) {
                cache.entrySet().stream()
                    .min(Map.Entry.comparingByValue(
                        (a, b) -> a.insertedAt().compareTo(b.insertedAt())))
                    .ifPresent(oldest -> cache.remove(oldest.getKey()));
            }
        }
    }
}
