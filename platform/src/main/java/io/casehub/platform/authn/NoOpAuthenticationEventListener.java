package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpAuthenticationEventListener implements AuthenticationEventListener {}
