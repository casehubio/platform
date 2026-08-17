package io.casehub.client;

import io.casehub.client.dto.LedgerEntry;
import io.casehub.client.dto.LedgerEntryPage;
import io.casehub.client.dto.TrustProfile;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerClientTest {

    @Test
    void isAnnotatedAsGraphQLClientApi() {
        assertThat(LedgerClient.class.isAnnotationPresent(GraphQLClientApi.class)).isTrue();
        assertThat(LedgerClient.class.getAnnotation(GraphQLClientApi.class).configKey())
                .isEqualTo("casehub");
    }

    @Test
    void ledgerEntryQueryExists() throws Exception {
        Method m = LedgerClient.class.getMethod("ledgerEntry", UUID.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(LedgerEntry.class);
    }

    @Test
    void trustScoreQueryExists() throws Exception {
        Method m = LedgerClient.class.getMethod("trustScore", String.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(TrustProfile.class);
    }
}
