package io.casehub.client;

import io.casehub.client.dto.WorkItem;
import io.casehub.client.dto.WorkItemPage;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;

import java.util.UUID;

@GraphQLClientApi(configKey = "casehub")
public interface WorkItemClient {

    @Query
    WorkItemPage workItems(PageInput page);

    @Query
    WorkItem workItemById(UUID id);

    @Mutation
    WorkItem claimWorkItem(UUID id, String claimant);

    @Mutation
    WorkItem startWorkItem(UUID id);

    @Mutation
    WorkItem completeWorkItem(UUID id);

    @Mutation
    WorkItem delegateWorkItem(UUID id, String targetActor);

    @Mutation
    WorkItem cancelWorkItem(UUID id);
}
