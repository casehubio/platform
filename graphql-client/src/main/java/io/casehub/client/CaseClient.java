package io.casehub.client;

import io.casehub.client.dto.CaseInstance;
import io.casehub.client.dto.CasePage;
import io.casehub.client.dto.StartCaseInput;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;

import java.util.UUID;

@GraphQLClientApi(configKey = "casehub")
public interface CaseClient {

    @Query
    CasePage cases(PageInput page);

    @Query
    CaseInstance caseById(UUID caseId);

    @Mutation
    CaseInstance startCase(StartCaseInput input);

    @Mutation
    CaseInstance suspendCase(UUID caseId);

    @Mutation
    CaseInstance resumeCase(UUID caseId);

    @Mutation
    CaseInstance cancelCase(UUID caseId);
}
