package io.casehub.client;

import io.casehub.client.dto.CaseInstance;
import io.casehub.client.dto.CasePage;
import io.casehub.client.dto.StartCaseInput;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseClientTest {

    @Test
    void isAnnotatedAsGraphQLClientApi() {
        assertThat(CaseClient.class.isAnnotationPresent(GraphQLClientApi.class)).isTrue();
        assertThat(CaseClient.class.getAnnotation(GraphQLClientApi.class).configKey())
                .isEqualTo("casehub");
    }

    @Test
    void casesQueryExists() throws Exception {
        Method m = CaseClient.class.getMethod("cases", PageInput.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(CasePage.class);
    }

    @Test
    void caseByIdQueryExists() throws Exception {
        Method m = CaseClient.class.getMethod("caseById", UUID.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(CaseInstance.class);
    }

    @Test
    void startCaseMutationExists() throws Exception {
        Method m = CaseClient.class.getMethod("startCase", StartCaseInput.class);
        assertThat(m.isAnnotationPresent(Mutation.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(CaseInstance.class);
    }
}
