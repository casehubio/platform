package io.casehub.platform.oidc.spring;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.MissingTenancyException;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.casehub.platform.api.identity.TenancyConstants;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.stream.Collectors;

public class SpringSecurityCurrentPrincipal implements CurrentPrincipal {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public String actorId() {
        Authentication auth = authentication();
        if (auth == null) return "anonymous";
        return auth.getName();
    }

    @Override
    public Set<String> groups() {
        Authentication auth = authentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String tenancyId() {
        Authentication auth = authentication();
        if (auth == null) return TenancyConstants.DEFAULT_TENANT_ID;

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String tenancy = jwt.getClaimAsString(SecurityIdentityAttributes.TENANCY_ID);
            if (tenancy != null && !tenancy.isBlank()) return tenancy;
        }

        throw new MissingTenancyException(auth.getName(),
                "Checked JWT claim '" + SecurityIdentityAttributes.TENANCY_ID + "'");
    }

    @Override
    public boolean isCrossTenantAdmin() {
        Authentication auth = authentication();
        if (auth == null) return false;

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Object raw = jwt.getClaim(SecurityIdentityAttributes.CROSS_TENANT_ADMIN);
            if (raw instanceof Boolean b) return b;
        }

        return false;
    }

    private Authentication authentication() {
        var context = SecurityContextHolder.getContext();
        Authentication auth = context.getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return null;
        return auth;
    }
}
