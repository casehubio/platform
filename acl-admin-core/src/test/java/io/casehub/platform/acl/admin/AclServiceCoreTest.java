package io.casehub.platform.acl.admin;

import io.casehub.platform.acl.inmem.InMemoryAccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AclServiceCoreTest {

    private InMemoryAccessControlProvider acl;
    private AclServiceCore service;

    private static final CurrentPrincipal ADMIN = new CurrentPrincipal() {
        @Override public String actorId() { return "admin-1"; }
        @Override public Set<String> groups() { return Set.of("platform-admin"); }
        @Override public String tenancyId() { return "tenant-1"; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private static CurrentPrincipal userPrincipal(String actorId) {
        return new CurrentPrincipal() {
            @Override public String actorId() { return actorId; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return "tenant-1"; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
    }

    @BeforeEach
    void setUp() {
        acl = new InMemoryAccessControlProvider((g, t) -> Set.of(), ADMIN);
        service = new AclServiceCore(acl, ADMIN);
    }

    @Test
    void grantAndCheck() {
        var resource = ResourceId.parse("case:123");
        service.grant(new AclEntryRequest("user-1", resource, AclAction.READ, null));
        var response = service.check("user-1", resource, AclAction.READ);
        assertThat(response.allowed()).isTrue();
    }

    @Test
    void revokeRemovesAccess() {
        var resource = ResourceId.parse("case:123");
        service.grant(new AclEntryRequest("user-1", resource, AclAction.READ, null));
        service.revoke("user-1", resource, AclAction.READ);
        var response = service.check("user-1", resource, AclAction.READ);
        assertThat(response.allowed()).isFalse();
    }

    @Test
    void nonAdminCanCheckOwnAccess() {
        var user = userPrincipal("user-1");
        var userAcl = new InMemoryAccessControlProvider((g, t) -> Set.of(), user);
        var selfService = new AclServiceCore(userAcl, user);
        userAcl.grant("user-1", ResourceId.parse("case:123"), AclAction.READ, null);
        var response = selfService.check("user-1", ResourceId.parse("case:123"), AclAction.READ);
        assertThat(response.allowed()).isTrue();
    }

    @Test
    void nonAdminCannotCheckOthersAccess() {
        var user = userPrincipal("user-1");
        var selfService = new AclServiceCore(acl, user);
        assertThatThrownBy(() -> selfService.check("user-2", ResourceId.parse("case:123"), AclAction.READ))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void nullParameterThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.revoke(null, ResourceId.parse("case:1"), AclAction.READ))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
