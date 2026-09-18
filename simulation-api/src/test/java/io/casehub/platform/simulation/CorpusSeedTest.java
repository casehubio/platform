package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorpusSeedTest {

    @Test
    void addAccumulatesRecords() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        seed.add("hello", 1);
        seed.add("world", 2);

        List<InvocationRecord<String, Integer>> records = seed.build();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).tenancyId()).isEqualTo("tenant-1");
        assertThat(records.get(0).input()).isEqualTo("hello");
        assertThat(records.get(0).output()).isEqualTo(1);
        assertThat(records.get(0).key()).isNull();
    }

    @Test
    void withKeyExtractorAutoDerivesKeys() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1")
                .withKeyExtractor(String::toUpperCase);
        seed.add("hello", 1);

        assertThat(seed.build().get(0).key()).isEqualTo("HELLO");
    }

    @Test
    void addWithExplicitKeyOverridesExtractor() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1")
                .withKeyExtractor(String::toUpperCase);
        seed.add("my-key", "hello", 1);

        assertThat(seed.build().get(0).key()).isEqualTo("my-key");
    }

    @Test
    void addWithTenantOverridesDefault() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        seed.add("tenant-2", "key", "hello", 1);

        assertThat(seed.build().get(0).tenancyId()).isEqualTo("tenant-2");
    }

    @Test
    void withOutputMapperEnablesSingleArgAdd() {
        var seed = new CorpusSeed<String, String>("spi.method", "tenant-1")
                .withOutputMapper(String::toUpperCase);
        seed.add("hello");

        assertThat(seed.build().get(0).output()).isEqualTo("HELLO");
    }

    @Test
    void addWithoutOutputMapperThrows() {
        var seed = new CorpusSeed<String, String>("spi.method", "tenant-1");

        assertThatThrownBy(() -> seed.add("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("withOutputMapper");
    }

    @Test
    void outputMapperAndKeyExtractorBothOperateOnRawInput() {
        var seed = new CorpusSeed<String, String>("spi.method", "tenant-1")
                .withKeyExtractor(String::toUpperCase)
                .withOutputMapper(s -> "out:" + s);
        seed.add("hello");

        var record = seed.build().get(0);
        assertThat(record.key()).isEqualTo("HELLO");
        assertThat(record.output()).isEqualTo("out:hello");
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedIntoCallsCorpusSeed() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        seed.add("hello", 1);

        var seeded = new AtomicReference<List<InvocationRecord<String, Integer>>>();
        SimulationCorpus<String, Integer> corpus = new SimulationCorpus<>() {
            @Override public Optional<Integer> lookupByKey(String qn, String key) { return Optional.empty(); }
            @Override public Optional<Integer> lookupByIndex(String qn, int index) { return Optional.empty(); }
            @Override public List<InvocationRecord<String, Integer>> list(String qn) { return List.of(); }
            @Override public List<InvocationRecord<String, Integer>> listByTenant(String qn, String t) { return List.of(); }
            @Override public void record(String qn, String t, String i, Integer o) {}
            @Override public void record(String qn, String t, String k, String i, Integer o) {}
            @Override public void seed(String qn, List<InvocationRecord<String, Integer>> records) { seeded.set(records); }
            @Override public void clear(String qn) {}
            @Override public int size(String qn) { return 0; }
        };

        seed.seedInto(corpus);
        assertThat(seeded.get()).hasSize(1);
        assertThat(seeded.get().get(0).input()).isEqualTo("hello");
    }

    @Test
    void qualifiedNameAndKeyExtractorAccessible() {
        KeyExtractor<String> extractor = s -> String.valueOf(s.length());
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1")
                .withKeyExtractor(extractor);

        assertThat(seed.qualifiedName()).isEqualTo("spi.method");
        assertThat(seed.keyExtractor()).isSameAs(extractor);
    }

    @Test
    void buildReturnsImmutableCopy() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        seed.add("hello", 1);
        List<InvocationRecord<String, Integer>> built = seed.build();

        seed.add("world", 2);
        assertThat(built).hasSize(1);
    }

    @Test
    void addReturnsSeedForChaining() {
        var seed = new CorpusSeed<String, Integer>("spi.method", "tenant-1");
        var result = seed.add("hello", 1).add("world", 2);
        assertThat(result).isSameAs(seed);
        assertThat(seed.build()).hasSize(2);
    }
}
