package io.casehub.platform.security.spring;

import io.casehub.platform.api.identity.MissingTenancyException;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentPrincipalTest {

    private final SecurityContextCurrentPrincipal principal =
            new SecurityContextCurrentPrincipal(TenancyConstants.DEFAULT_TENANT_ID);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- actorId ---

    @Test
    void actorId_returnsAnonymous_whenNoAuthentication() {
        assertThat(principal.actorId()).isEqualTo("anonymous");
    }

    @Test
    void actorId_returnsAnonymous_whenAnonymousToken() {
        var anon = new AnonymousAuthenticationToken("key", "anon",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anon);

        assertThat(principal.actorId()).isEqualTo("anonymous");
    }

    @Test
    void actorId_returnsName_fromAuthentication() {
        authenticate("alice", "ROLE_USER");

        assertThat(principal.actorId()).isEqualTo("alice");
    }

    // --- groups ---

    @Test
    void groups_returnsEmpty_whenNoAuthentication() {
        assertThat(principal.groups()).isEmpty();
    }

    @Test
    void groups_stripsRolePrefix() {
        authenticate("bob", "ROLE_ADMIN", "ROLE_USER", "custom-group");

        assertThat(principal.groups()).containsExactlyInAnyOrder("ADMIN", "USER", "custom-group");
    }

    @Test
    void groups_returnsEmpty_forAnonymousToken() {
        var anon = new AnonymousAuthenticationToken("key", "anon",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anon);

        assertThat(principal.groups()).isEmpty();
    }

    // --- tenancyId ---

    @Test
    void tenancyId_returnsDefault_whenNoAuthentication() {
        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void tenancyId_readsFromDetailsMap() {
        var auth = new UsernamePasswordAuthenticationToken("alice", "secret",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails(Map.of(SecurityIdentityAttributes.TENANCY_ID, "tenant-42"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(principal.tenancyId()).isEqualTo("tenant-42");
    }

    @Test
    void tenancyId_returnsDefault_whenDetailsMapHasNoTenancy() {
        var auth = new UsernamePasswordAuthenticationToken("alice", "secret",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails(Map.of("other", "value"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void tenancyId_returnsDefault_whenDetailsNotMap() {
        var auth = new UsernamePasswordAuthenticationToken("alice", "secret",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails("some-string-details");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void tenancyId_returnsDefault_whenDetailsNull() {
        authenticate("alice", "ROLE_USER");

        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void tenancyId_ignoresBlankValue_inDetailsMap() {
        var auth = new UsernamePasswordAuthenticationToken("alice", "secret",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails(Map.of(SecurityIdentityAttributes.TENANCY_ID, "  "));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    // --- isCrossTenantAdmin ---

    @Test
    void crossTenantAdmin_false_whenNoAuthentication() {
        assertThat(principal.isCrossTenantAdmin()).isFalse();
    }

    @Test
    void crossTenantAdmin_readsFromDetailsMap() {
        var auth = new UsernamePasswordAuthenticationToken("admin", "secret",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        auth.setDetails(Map.of(SecurityIdentityAttributes.CROSS_TENANT_ADMIN, true));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(principal.isCrossTenantAdmin()).isTrue();
    }

    @Test
    void crossTenantAdmin_false_whenNotInDetailsMap() {
        authenticate("alice", "ROLE_USER");

        assertThat(principal.isCrossTenantAdmin()).isFalse();
    }

    @Test
    void crossTenantAdmin_false_whenDetailsNotMap() {
        var auth = new UsernamePasswordAuthenticationToken("alice", "secret",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails("string-details");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(principal.isCrossTenantAdmin()).isFalse();
    }

    // --- configurable default tenancy ---

    @Test
    void tenancyId_usesCustomDefault_whenConfigured() {
        var custom = new SecurityContextCurrentPrincipal("custom-tenant");
        authenticate("alice", "ROLE_USER");

        assertThat(custom.tenancyId()).isEqualTo("custom-tenant");
    }

    // --- isAuthenticated (inherited default) ---

    @Test
    void isAuthenticated_false_whenNoAuth() {
        assertThat(principal.isAuthenticated()).isFalse();
    }

    @Test
    void isAuthenticated_true_whenAuthenticated() {
        authenticate("alice", "ROLE_USER");
        assertThat(principal.isAuthenticated()).isTrue();
    }

    private void authenticate(String username, String... authorities) {
        var auth = new TestingAuthenticationToken(username, "credentials",
                authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
