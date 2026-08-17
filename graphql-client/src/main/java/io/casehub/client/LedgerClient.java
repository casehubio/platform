package io.casehub.client;

import io.casehub.client.dto.LedgerEntry;
import io.casehub.client.dto.LedgerEntryPage;
import io.casehub.client.dto.TrustProfile;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Query;

import java.util.UUID;

@GraphQLClientApi(configKey = "casehub")
public interface LedgerClient {

    @Query
    LedgerEntryPage ledgerEntries(UUID subjectId, PageInput page);

    @Query
    LedgerEntry ledgerEntry(UUID id);

    @Query
    TrustProfile trustScore(String actorId);
}
