package io.casehub.platform.api.delivery;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationResolverContractTest {

    @Test
    void resolve_returnsDestinationForKnownUser() {
        DestinationResolver resolver = new DestinationResolver() {
            @Override
            public String channelId() { return "email"; }

            @Override
            public Optional<String> resolve(String userId, String tenancyId) {
                if ("user-1".equals(userId)) return Optional.of("user1@example.com");
                return Optional.empty();
            }
        };

        assertThat(resolver.channelId()).isEqualTo("email");
        assertThat(resolver.resolve("user-1", "tenant-1")).contains("user1@example.com");
    }

    @Test
    void resolve_returnsEmptyForUnknownUser() {
        DestinationResolver resolver = new DestinationResolver() {
            @Override
            public String channelId() { return "sms"; }

            @Override
            public Optional<String> resolve(String userId, String tenancyId) {
                return Optional.empty();
            }
        };

        assertThat(resolver.resolve("unknown", "tenant-1")).isEmpty();
    }
}
