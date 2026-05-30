package io.casehub.platform.scim;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(ScimWireMockResource.class)
class ScimGroupMembershipProviderTest {

    @Inject GroupMembershipProvider provider;

    private WireMockServer wireMock() {
        return ScimWireMockResource.INSTANCE;
    }

    @BeforeEach
    void resetStubs() {
        wireMock().resetAll();
    }

    // Each test uses a unique group name so @CacheResult doesn't cause cross-test cache hits.
    // The Quarkus application (and its cache) is shared across all tests in this class.
    // If you add a test, use a group name not used by any other test in this class.

    @Test
    void group_found_members_inline() {
        stubScimGroup("inline-reviewers", "group-g1",
            "[{ \"value\": \"alice-uuid\", \"display\": \"Alice Smith\" }," +
            " { \"value\": \"bob-uuid\",   \"display\": \"Bob Jones\"  }]");

        Set<GroupMember> members = provider.membersOf("inline-reviewers");

        assertEquals(2, members.size());
        assertTrue(members.contains(new GroupMember("alice-uuid", "Alice Smith")));
        assertTrue(members.contains(new GroupMember("bob-uuid", "Bob Jones")));
        wireMock().verify(1, getRequestedFor(urlPathEqualTo("/Groups")));
        wireMock().verify(0, getRequestedFor(urlPathMatching("/Groups/.*")));
    }

    @Test
    void group_found_members_require_second_call() {
        wireMock().stubFor(get(urlPathEqualTo("/Groups"))
            .withQueryParam("filter", equalTo("displayName eq \"second-call-reviewers\""))
            .willReturn(okJson(
                "{ \"totalResults\": 1, \"Resources\": " +
                "[{ \"id\": \"group-g2\", \"displayName\": \"second-call-reviewers\" }] }")));
        wireMock().stubFor(get(urlPathEqualTo("/Groups/group-g2"))
            .willReturn(okJson(
                "{ \"id\": \"group-g2\", \"displayName\": \"second-call-reviewers\"," +
                "  \"members\": [{ \"value\": \"carol-uuid\", \"display\": \"Carol White\" }] }")));

        Set<GroupMember> members = provider.membersOf("second-call-reviewers");

        assertEquals(1, members.size());
        assertTrue(members.contains(new GroupMember("carol-uuid", "Carol White")));
        wireMock().verify(1, getRequestedFor(urlPathEqualTo("/Groups")));
        wireMock().verify(1, getRequestedFor(urlPathEqualTo("/Groups/group-g2")));
    }

    @Test
    void group_not_found_returns_empty() {
        wireMock().stubFor(get(urlPathEqualTo("/Groups"))
            .withQueryParam("filter", equalTo("displayName eq \"nonexistent-group\""))
            .willReturn(okJson("{ \"totalResults\": 0, \"Resources\": [] }")));

        assertEquals(Set.of(), provider.membersOf("nonexistent-group"));
    }

    @Test
    void scim_error_propagates_exception() {
        wireMock().stubFor(get(urlPathEqualTo("/Groups"))
            .withQueryParam("filter", equalTo("displayName eq \"error-group\""))
            .willReturn(serverError()));

        assertThrows(RuntimeException.class, () -> provider.membersOf("error-group"));
    }

    @Test
    void actorId_is_scim_value_not_display() {
        stubScimGroup("auditors", "group-g3",
            "[{ \"value\": \"uuid-stable-123\", \"display\": \"Could Change Display\" }]");

        GroupMember member = provider.membersOf("auditors").iterator().next();

        assertEquals("uuid-stable-123", member.actorId());
        assertEquals("Could Change Display", member.displayName());
    }

    @Test
    void result_is_cached_scim_called_only_once_for_same_group() {
        stubScimGroup("cached-group", "group-g4",
            "[{ \"value\": \"dave-uuid\", \"display\": \"Dave\" }]");

        provider.membersOf("cached-group");
        provider.membersOf("cached-group");

        wireMock().verify(1, getRequestedFor(urlPathEqualTo("/Groups")));
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private void stubScimGroup(String groupName, String groupId, String membersJson) {
        wireMock().stubFor(get(urlPathEqualTo("/Groups"))
            .withQueryParam("filter", equalTo("displayName eq \"" + groupName + "\""))
            .willReturn(okJson(
                "{ \"totalResults\": 1, \"Resources\": " +
                "[{ \"id\": \"" + groupId + "\", \"displayName\": \"" + groupName + "\"," +
                "   \"members\": " + membersJson + " }] }")));
    }
}
