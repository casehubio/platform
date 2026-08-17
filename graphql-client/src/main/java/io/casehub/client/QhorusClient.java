package io.casehub.client;

import io.casehub.client.dto.Channel;
import io.casehub.client.dto.ChannelPage;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Query;

import java.util.UUID;

@GraphQLClientApi(configKey = "casehub")
public interface QhorusClient {

    @Query
    ChannelPage channels(PageInput page);

    @Query
    Channel channel(UUID id);
}
