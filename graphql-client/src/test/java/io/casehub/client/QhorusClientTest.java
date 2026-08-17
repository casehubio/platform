package io.casehub.client;

import io.casehub.client.dto.Channel;
import io.casehub.client.dto.ChannelPage;
import io.casehub.client.dto.DispatchResult;
import io.casehub.platform.graphql.PageInput;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Query;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QhorusClientTest {

    @Test
    void isAnnotatedAsGraphQLClientApi() {
        assertThat(QhorusClient.class.isAnnotationPresent(GraphQLClientApi.class)).isTrue();
        assertThat(QhorusClient.class.getAnnotation(GraphQLClientApi.class).configKey())
                .isEqualTo("casehub");
    }

    @Test
    void channelsQueryExists() throws Exception {
        Method m = QhorusClient.class.getMethod("channels", PageInput.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(ChannelPage.class);
    }

    @Test
    void channelByIdQueryExists() throws Exception {
        Method m = QhorusClient.class.getMethod("channel", UUID.class);
        assertThat(m.isAnnotationPresent(Query.class)).isTrue();
        assertThat(m.getReturnType()).isEqualTo(Channel.class);
    }
}
