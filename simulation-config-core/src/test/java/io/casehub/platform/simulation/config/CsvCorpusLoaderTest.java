package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCorpusLoaderTest {

    private final CsvCorpusLoader loader = new CsvCorpusLoader();

    @Test
    void supportsCsvExtension() {
        assertThat(loader.supports("data.csv")).isTrue();
        assertThat(loader.supports("data.CSV")).isTrue();
        assertThat(loader.supports("data.yaml")).isFalse();
    }

    @Test
    void loadsCsvWithAllConventionColumns() {
        String csv = """
                _qualified_name,_key,_tenancy_id,accountId,name,balance
                bank-feed.balance,acct-123,tenant-a,acct-123,Checking,1234.56
                bank-feed.balance,acct-456,tenant-a,acct-456,Savings,5678.90
                """;
        var result = loader.load(stream(csv), "default-t");

        assertThat(result).containsKey("bank-feed.balance");
        List<InvocationRecord<Object, Object>> records = result.get("bank-feed.balance");
        assertThat(records).hasSize(2);
        assertThat(records.get(0).key()).isEqualTo("acct-123");
        assertThat(records.get(0).tenancyId()).isEqualTo("tenant-a");

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) records.get(0).output();
        assertThat(output).containsEntry("accountId", "acct-123");
        assertThat(output).containsEntry("name", "Checking");
        assertThat(output).containsEntry("balance", "1234.56");
    }

    @Test
    void fallsBackToDefaultTenancyId() {
        String csv = """
                _qualified_name,_key,value
                spi.method,k1,hello
                """;
        var result = loader.load(stream(csv), "fallback-tenant");

        assertThat(result.get("spi.method").get(0).tenancyId()).isEqualTo("fallback-tenant");
    }

    @Test
    void missingQualifiedNameColumnThrows() {
        String csv = """
                _key,value
                k1,hello
                """;
        assertThatThrownBy(() -> loader.load(stream(csv), "t1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("_qualified_name");
    }

    @Test
    void emptyCsvReturnsEmptyMap() {
        var result = loader.load(stream(""), "t1");
        assertThat(result).isEmpty();
    }

    @Test
    void groupsRowsByQualifiedName() {
        String csv = """
                _qualified_name,_key,value
                spi.method-a,k1,hello
                spi.method-b,k2,world
                spi.method-a,k3,again
                """;
        var result = loader.load(stream(csv), "t1");
        assertThat(result).hasSize(2);
        assertThat(result.get("spi.method-a")).hasSize(2);
        assertThat(result.get("spi.method-b")).hasSize(1);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
