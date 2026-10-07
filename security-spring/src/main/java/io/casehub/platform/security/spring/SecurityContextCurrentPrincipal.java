package io.casehub.platform.security.spring;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SecurityContextCurrentPrincipal implements CurrentPrincipal {

    private static final String ROLE_PREFIX = "ROLE_";

    private final String defaultTenancyId;

    public SecurityContextCurrentPrincipal(String defaultTenancyId) {
        this.defaultTenancyId = defaultTenancyId;
    }

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
        if (auth == null) return defaultTenancyId;

        String tenancy = detailsString(auth, SecurityIdentityAttributes.TENANCY_ID);
        if (tenancy != null && !tenancy.isBlank()) return tenancy;

        return defaultTenancyId;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        Authentication auth = authentication();
        if (auth == null) return false;

        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object raw = map.get(SecurityIdentityAttributes.CROSS_TENANT_ADMIN);
            if (raw instanceof Boolean b) return b;
        }
        return false;
    }

    private String detailsString(Authentication auth, String key) {
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object raw = map.get(key);
            if (raw instanceof String s) return s;
        }
        return null;
    }

    private Authentication authentication() {
        var context = SecurityContextHolder.getContext();
        Authentication auth = context.getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return null;
        return auth;
    }
}
