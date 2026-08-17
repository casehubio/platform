package io.casehub.client;

import io.casehub.client.dto.WorkItem;
import io.casehub.client.dto.WorkItemPage;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemClientTest {

    @Test
    void isAnnotatedAsGraphQLClientApi() {
        assertThat(WorkItemClient.class.isAnnotationPresent(GraphQLClientApi.class)).isTrue();
        assertThat(WorkItemClient.class.getAnnotation(GraphQLClientApi.class).configKey())
                .isEqualTo("casehub");
    }

    @Test
    void workItemsQueryExists() throws Exception {
        Method m = WorkItemClient.class.getMethod("workItems", PageInput.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(WorkItemPage.class);
    }

    @Test
    void workItemByIdQueryExists() throws Exception {
        Method m = WorkItemClient.class.getMethod("workItemById", UUID.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(WorkItem.class);
    }

    @Test
    void claimWorkItemMutationExists() throws Exception {
        Method m = WorkItemClient.class.getMethod("claimWorkItem", UUID.class, String.class);
        assertThat(m.isAnnotationPresent(Mutation.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(WorkItem.class);
    }

    @Test
    void completeWorkItemMutationExists() throws Exception {
        Method m = WorkItemClient.class.getMethod("completeWorkItem", UUID.class);
        assertThat(m.isAnnotationPresent(Mutation.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(WorkItem.class);
    }
}
