package io.casehub.platform.scim;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;
import io.casehub.platform.scim.model.ScimMemberRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScimGroupMembershipProviderCoreTest {

    @Test
    void membersOf_returns_group_members() {
        var client = stubClient(
                new ScimListResponse<>(1, 1, 100, List.of(
                        new ScimGroupResource("g1", "engineers",
                                List.of(new ScimMemberRef("u1", "Alice"),
                                        new ScimMemberRef("u2", "Bob"))))));

        var provider = new ScimGroupMembershipProviderCore(client, () -> 1000);
        Set<GroupMember> members = provider.membersOf("engineers", "tenant1");

        assertThat(members).containsExactlyInAnyOrder(
                new GroupMember("u1", "Alice"),
                new GroupMember("u2", "Bob"));
    }

    @Test
    void membersOf_unknown_group_returns_empty() {
        var client = stubClient(new ScimListResponse<>(0, 1, 100, List.of()));
        var provider = new ScimGroupMembershipProviderCore(client, () -> 1000);

        assertThat(provider.membersOf("nonexistent", "t1")).isEmpty();
    }

    @Test
    void membersOf_null_resources_returns_empty() {
        var client = stubClient(new ScimListResponse<>(0, 1, 100, null));
        var provider = new ScimGroupMembershipProviderCore(client, () -> 1000);

        assertThat(provider.membersOf("empty", "t1")).isEmpty();
    }

    @Test
    void membersOf_null_members_returns_empty() {
        var client = stubClient(
                new ScimListResponse<>(1, 1, 100, List.of(
                        new ScimGroupResource("g1", "empty-group", null))));

        var provider = new ScimGroupMembershipProviderCore(client, () -> 1000);
        assertThat(provider.membersOf("empty-group", "t1")).isEmpty();
    }

    @Test
    void membersOf_escapes_groupName() {
        var captureClient = new CaptureFilterScimClient();
        var provider = new ScimGroupMembershipProviderCore(captureClient, () -> 1000);
        provider.membersOf("group\"with\\special", "t1");

        assertThat(captureClient.lastFilter).isEqualTo("displayName eq \"group\\\"with\\\\special\"");
    }

    private static ScimClient stubClient(ScimListResponse<ScimGroupResource> listResponse) {
        return new ScimClient() {
            @Override
            public ScimListResponse<ScimGroupResource> listGroups(String filter, String attributes) {
                return listResponse;
            }

            @Override
            public ScimGroupResource getGroup(String id, String attributes) {
                return listResponse.resources() != null && !listResponse.resources().isEmpty()
                        ? listResponse.resources().get(0) : null;
            }

            @Override
            public ScimGroupResource getGroup(String id, String attributes, int startIndex, int count) {
                return getGroup(id, attributes);
            }
        };
    }

    private static class CaptureFilterScimClient implements ScimClient {
        String lastFilter;

        @Override
        public ScimListResponse<ScimGroupResource> listGroups(String filter, String attributes) {
            this.lastFilter = filter;
            return new ScimListResponse<>(0, 1, 100, List.of());
        }

        @Override
        public ScimGroupResource getGroup(String id, String attributes) { return null; }

        @Override
        public ScimGroupResource getGroup(String id, String attributes, int s, int c) { return null; }
    }
}
