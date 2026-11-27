package io.casehub.template.registry.llm;

import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateMatch;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationCacheTest {

    @Test
    void get_returnsNullForUnknownKey() {
        var cache = new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input");
        assertThat(cache.get(key)).isNull();
    }

    @Test
    void putPositive_thenGet_returnsCachedResult() {
        var cache = new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns",
            "I'm in favour of this design");
        var matched = new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "I'm in favour of this ${subject}", 0.9);
        cache.putPositive(key, matched);

        var entry = cache.get(key);
        assertThat(entry).isNotNull();
        assertThat(entry.isPositive()).isTrue();
        assertThat(entry.matched().templateName()).isEqualTo("vote");
    }

    @Test
    void putNegative_thenGet_returnsNegativeEntry() {
        var cache = new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns", "unrelated text");
        cache.putNegative(key);

        var entry = cache.get(key);
        assertThat(entry).isNotNull();
        assertThat(entry.isPositive()).isFalse();
    }

    @Test
    void invalidateNamespace_clearsAllEntriesForNamespace() {
        var cache = new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key1 = new ClassificationCache.CacheKey("ns1", "input1");
        var key2 = new ClassificationCache.CacheKey("ns1", "input2");
        var key3 = new ClassificationCache.CacheKey("ns2", "input1");
        cache.putNegative(key1);
        cache.putNegative(key2);
        cache.putNegative(key3);

        cache.invalidateNamespace("ns1");

        assertThat(cache.get(key1)).isNull();
        assertThat(cache.get(key2)).isNull();
        assertThat(cache.get(key3)).isNotNull();
    }

    @Test
    void negativeTtl_expiresEntry() throws InterruptedException {
        var cache = new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMillis(50), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input");
        cache.putNegative(key);
        Thread.sleep(100);
        assertThat(cache.get(key)).isNull();
    }

    @Test
    void positiveTtl_expiresEntry() throws InterruptedException {
        var cache = new ClassificationCache(
            Duration.ofMillis(50), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input");
        var matched = new ClassificationResult.Matched(
            "t", Map.of(), "p", 0.9);
        cache.putPositive(key, matched);
        Thread.sleep(100);
        assertThat(cache.get(key)).isNull();
    }

    @Test
    void toTemplateMatch_positiveEntry() {
        var matched = new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve"), "pattern", 0.9);
        var entry = new ClassificationCache.Entry(matched, java.time.Instant.now());

        var tm = entry.toTemplateMatch("ns");
        assertThat(tm).isInstanceOf(TemplateMatch.Matched.class);
        var m = (TemplateMatch.Matched) tm;
        assertThat(m.namespace()).isEqualTo("ns");
        assertThat(m.templateName()).isEqualTo("vote");
        assertThat(m.score()).isEqualTo(0.9);
        assertThat(m.patternIndex()).isEqualTo(-1);
    }

    @Test
    void toTemplateMatch_negativeEntry() {
        var entry = new ClassificationCache.Entry(null, java.time.Instant.now());
        assertThat(entry.toTemplateMatch("ns")).isNull();
    }

    @Test
    void maxEntries_evictsOldest() {
        var cache = new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMinutes(5), 2);
        var key1 = new ClassificationCache.CacheKey("ns", "first");
        var key2 = new ClassificationCache.CacheKey("ns", "second");
        var key3 = new ClassificationCache.CacheKey("ns", "third");
        cache.putNegative(key1);
        cache.putNegative(key2);
        cache.putNegative(key3);

        // After adding 3 items to a cache of size 2, at least one early item evicted
        int remaining = 0;
        if (cache.get(key1) != null) remaining++;
        if (cache.get(key2) != null) remaining++;
        if (cache.get(key3) != null) remaining++;
        assertThat(remaining).isLessThanOrEqualTo(2);
    }

    @Test
    void embeddingPositive_cachedAndReturned() {
        var cache = new ClassificationCache(
                Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input text");
        cache.putEmbeddingPositive(key, "template-name", 0, Map.of("field", "value"), 0.75);
        var entry = cache.getEmbedding(key);
        assertThat(entry).isNotNull();
        assertThat(entry.isPositive()).isTrue();
        assertThat(entry.templateName()).isEqualTo("template-name");
        assertThat(entry.similarity()).isEqualTo(0.75);
    }

    @Test
    void embeddingNegative_cachedAndReturned() {
        var cache = new ClassificationCache(
                Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input text");
        cache.putEmbeddingNegative(key);
        var entry = cache.getEmbedding(key);
        assertThat(entry).isNotNull();
        assertThat(entry.isPositive()).isFalse();
    }

    @Test
    void embeddingCache_invalidatedOnNamespaceReplace() {
        var cache = new ClassificationCache(
                Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input text");
        cache.putEmbeddingPositive(key, "template-name", 0, Map.of(), 0.75);
        cache.invalidateNamespace("ns");
        assertThat(cache.getEmbedding(key)).isNull();
    }

    @Test
    void embeddingCache_ttlExpires() throws InterruptedException {
        var cache = new ClassificationCache(
                Duration.ofMillis(50), Duration.ofMillis(50), 1000);
        var key = new ClassificationCache.CacheKey("ns", "input text");
        cache.putEmbeddingPositive(key, "template-name", 0, Map.of(), 0.75);
        Thread.sleep(100);
        assertThat(cache.getEmbedding(key)).isNull();
    }

}
