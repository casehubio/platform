package io.casehub.platform.authn.social.jpa;

import java.io.Serializable;
import java.util.Objects;

public class IdentityBindingId implements Serializable {

    public String provider;
    public String externalId;
    public String tenancyId;

    public IdentityBindingId() {}

    public IdentityBindingId(String provider, String externalId, String tenancyId) {
        this.provider = provider;
        this.externalId = externalId;
        this.tenancyId = tenancyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentityBindingId that)) return false;
        return Objects.equals(provider, that.provider)
                && Objects.equals(externalId, that.externalId)
                && Objects.equals(tenancyId, that.tenancyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, externalId, tenancyId);
    }
}
