package io.casehub.platform.simulation.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RandomCorpusPopulatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    record SimpleInput(String name, int value) {}

    record SimpleOutput(boolean success, String message) {}

    @Test
    void populates_corpus_seed_with_typed_random_data() {
        CorpusSeed<SimpleInput, SimpleOutput> seed = new CorpusSeed<>("test-spi.method", "tenant-1");

        RandomCorpusPopulator.populate(seed, SimpleInput.class, SimpleOutput.class, 5, mapper);

        List<InvocationRecord<SimpleInput, SimpleOutput>> records = seed.build();
        assertThat(records).hasSize(5);
        for (var record : records) {
            assertThat(record.input()).isNotNull();
            assertThat(record.input().name()).isNotNull();
            assertThat(record.output()).isNotNull();
            assertThat(record.output().message()).isNotNull();
        }
    }

    @Test
    void generated_records_have_distinct_values() {
        CorpusSeed<SimpleInput, SimpleOutput> seed = new CorpusSeed<>("test-spi.method", "tenant-1");

        RandomCorpusPopulator.populate(seed, SimpleInput.class, SimpleOutput.class, 10, mapper);

        List<InvocationRecord<SimpleInput, SimpleOutput>> records = seed.build();
        long distinctNames = records.stream()
                .map(r -> r.input().name())
                .distinct()
                .count();
        assertThat(distinctNames).isGreaterThan(1);
    }
}
