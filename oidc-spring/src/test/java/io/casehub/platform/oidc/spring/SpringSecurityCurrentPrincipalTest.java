package io.casehub.platform.oidc.spring;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.MissingTenancyException;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.casehub.platform.api.identity.TenancyConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringSecurityCurrentPrincipalTest {

    private final SpringSecurityCurrentPrincipal principal = new SpringSecurityCurrentPrincipal();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String subject, Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private void setJwtAuth(String subject, List<String> authorities, Map<String, Object> claims) {
        var jwt = jwt(subject, claims);
        var grants = authorities.stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, grants, subject));
    }

    @Test
    void jwt_all_claims_present() {
        setJwtAuth("alice", List.of("admin", "reviewer"),
                Map.of(SecurityIdentityAttributes.TENANCY_ID, "tenant-abc",
                       SecurityIdentityAttributes.CROSS_TENANT_ADMIN, true));

        assertThat(principal.actorId()).isEqualTo("alice");
        assertThat(principal.groups()).containsExactlyInAnyOrder("admin", "reviewer");
        assertThat(principal.tenancyId()).isEqualTo("tenant-abc");
        assertThat(principal.isCrossTenantAdmin()).isTrue();
        assertThat(principal.isAuthenticated()).isTrue();
    }

    @Test
    void jwt_crossTenantAdmin_absent_returns_false() {
        setJwtAuth("alice", List.of(), Map.of(SecurityIdentityAttributes.TENANCY_ID, "t1"));
        assertThat(principal.isCrossTenantAdmin()).isFalse();
    }

    @Test
    void jwt_tenancyId_missing_throws() {
        setJwtAuth("alice", List.of(), Map.of());
        assertThatThrownBy(() -> principal.tenancyId())
                .isInstanceOf(MissingTenancyException.class);
    }

    @Test
    void jwt_tenancyId_blank_throws() {
        setJwtAuth("alice", List.of(),
                Map.of(SecurityIdentityAttributes.TENANCY_ID, "  "));
        assertThatThrownBy(() -> principal.tenancyId())
                .isInstanceOf(MissingTenancyException.class);
    }

    @Test
    void jwt_strips_role_prefix_from_authorities() {
        setJwtAuth("alice", List.of("ROLE_admin", "viewer"), Map.of(
                SecurityIdentityAttributes.TENANCY_ID, "t1"));
        assertThat(principal.groups()).containsExactlyInAnyOrder("admin", "viewer");
    }

    @Test
    void jwt_system_principal() {
        setJwtAuth("system:scheduler", List.of(), Map.of(
                SecurityIdentityAttributes.TENANCY_ID, "t1"));
        assertThat(principal.isSystem()).isTrue();
        assertThat(principal.actorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void null_authentication_returns_anonymous() {
        SecurityContextHolder.clearContext();
        assertThat(principal.actorId()).isEqualTo("anonymous");
        assertThat(principal.groups()).isEmpty();
        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(principal.isCrossTenantAdmin()).isFalse();
        assertThat(principal.isAuthenticated()).isFalse();
    }

    @Test
    void anonymous_token_returns_sentinels() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anon",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(principal.actorId()).isEqualTo("anonymous");
        assertThat(principal.tenancyId()).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }
}
