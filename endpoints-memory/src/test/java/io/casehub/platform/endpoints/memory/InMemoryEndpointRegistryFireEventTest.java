package io.casehub.platform.endpoints.memory;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointRegistered;
import io.casehub.platform.api.endpoints.EndpointType;
import io.casehub.platform.api.path.Path;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InMemoryEndpointRegistryFireEventTest {

    @SuppressWarnings("unchecked")
    private final Event<EndpointRegistered> event = mock(Event.class);
    private InMemoryEndpointRegistry registry;

    @BeforeEach
    void setUp() {
        when(event.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        registry = new InMemoryEndpointRegistry(event);
    }

    private static EndpointDescriptor descriptor(Path path, String tenancyId) {
        return new EndpointDescriptor(
            path, tenancyId, EndpointType.SERVICE, EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, "https://example.com"),
            null, Set.of(EndpointCapability.SEND));
    }

    @Test
    void register_firesEndpointRegisteredWithCorrectDescriptor() {
        var desc = descriptor(Path.of("test", "endpoint"), "tenant-a");

        registry.register(desc);

        var captor = ArgumentCaptor.forClass(EndpointRegistered.class);
        verify(event).fireAsync(captor.capture());
        assertThat(captor.getValue().descriptor()).isSameAs(desc);
    }

    @Test
    void register_calledTwice_firesTwoEvents() {
        var desc1 = descriptor(Path.of("first", "endpoint"), "tenant-a");
        var desc2 = descriptor(Path.of("second", "endpoint"), "tenant-a");

        registry.register(desc1);
        registry.register(desc2);

        verify(event, times(2)).fireAsync(any(EndpointRegistered.class));
    }

    @Test
    void register_upsertSameKey_stillFiresEvent() {
        var path = Path.of("same", "path");
        var original = descriptor(path, "tenant-a");
        var updated = new EndpointDescriptor(
            path, "tenant-a", EndpointType.SYSTEM, EndpointProtocol.GRPC,
            Map.of(EndpointPropertyKeys.URL, "https://updated.com"),
            null, Set.of(EndpointCapability.QUERY));

        registry.register(original);
        registry.register(updated);

        var captor = ArgumentCaptor.forClass(EndpointRegistered.class);
        verify(event, times(2)).fireAsync(captor.capture());
        assertThat(captor.getAllValues().get(1).descriptor()).isSameAs(updated);
    }
}
