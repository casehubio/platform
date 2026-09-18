package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvocationRecordFactoryTest {

    @Test
    void ofWithoutKeyDefaultsKeyToNull() {
        InvocationRecord<String, Integer> record = InvocationRecord.of("tenant-1", "hello", 42);
        assertThat(record.tenancyId()).isEqualTo("tenant-1");
        assertThat(record.input()).isEqualTo("hello");
        assertThat(record.output()).isEqualTo(42);
        assertThat(record.key()).isNull();
        assertThat(record.recordedAt()).isNotNull();
    }

    @Test
    void ofWithKeyPreservesKey() {
        InvocationRecord<String, Integer> record = InvocationRecord.of("tenant-1", "my-key", "hello", 42);
        assertThat(record.tenancyId()).isEqualTo("tenant-1");
        assertThat(record.key()).isEqualTo("my-key");
        assertThat(record.input()).isEqualTo("hello");
        assertThat(record.output()).isEqualTo(42);
        assertThat(record.recordedAt()).isNotNull();
    }
}
