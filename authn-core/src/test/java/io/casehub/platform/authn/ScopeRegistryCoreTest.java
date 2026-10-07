package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ScopeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScopeRegistryCoreTest {

    private ScopeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ScopeRegistryCore();
    }

    @Test
    void registerAndQueryScopes() {
        registry.register("google",
                Set.of("calendar.readonly", "calendar.events"),
                FakeCalendarConsumer.class);

        assertEquals(
                Set.of("calendar.readonly", "calendar.events"),
                registry.requiredScopes("google"));
    }

    @Test
    void requiredScopesForUnknownProviderReturnsEmpty() {
        assertEquals(Set.of(), registry.requiredScopes("unknown"));
    }

    @Test
    void requiredScopesByConsumer() {
        registry.register("google",
                Set.of("calendar.readonly"), FakeCalendarConsumer.class);
        registry.register("google",
                Set.of("drive.readonly"), FakeDriveConsumer.class);

        assertEquals(
                Set.of("calendar.readonly"),
                registry.requiredScopes("google", FakeCalendarConsumer.class));
        assertEquals(
                Set.of("calendar.readonly", "drive.readonly"),
                registry.requiredScopes("google"));
    }

    @Test
    void satisfiesReturnsTrueWhenAllScopesGranted() {
        registry.register("google",
                Set.of("calendar.readonly", "profile"),
                FakeCalendarConsumer.class);

        assertTrue(registry.satisfies("google",
                Set.of("calendar.readonly", "profile", "email")));
    }

    @Test
    void satisfiesReturnsFalseWhenScopesMissing() {
        registry.register("google",
                Set.of("calendar.readonly", "calendar.events"),
                FakeCalendarConsumer.class);

        assertFalse(registry.satisfies("google",
                Set.of("calendar.readonly")));
    }

    @Test
    void missingScopesReturnsOnlyMissing() {
        registry.register("google",
                Set.of("calendar.readonly", "calendar.events"),
                FakeCalendarConsumer.class);

        assertEquals(
                Set.of("calendar.events"),
                registry.missingScopes("google",
                        Set.of("calendar.readonly", "profile")));
    }

    @Test
    void missingScopesReturnsEmptyWhenAllGranted() {
        registry.register("google",
                Set.of("calendar.readonly"),
                FakeCalendarConsumer.class);

        assertEquals(Set.of(),
                registry.missingScopes("google",
                        Set.of("calendar.readonly", "profile")));
    }

    @Test
    void satisfiesReturnsTrueForUnknownProvider() {
        assertTrue(registry.satisfies("unknown", Set.of()));
    }

    static class FakeCalendarConsumer {}
    static class FakeDriveConsumer {}
}
