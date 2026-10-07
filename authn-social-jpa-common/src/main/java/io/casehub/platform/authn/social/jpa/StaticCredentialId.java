package io.casehub.platform.authn.social.jpa;

import java.io.Serializable;
import java.util.Objects;

public class StaticCredentialId implements Serializable {

    public String actorId;
    public String provider;
    public String tenancyId;

    public StaticCredentialId() {}

    public StaticCredentialId(String actorId, String provider, String tenancyId) {
        this.actorId = actorId;
        this.provider = provider;
        this.tenancyId = tenancyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaticCredentialId that)) return false;
        return Objects.equals(actorId, that.actorId)
                && Objects.equals(provider, that.provider)
                && Objects.equals(tenancyId, that.tenancyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorId, provider, tenancyId);
    }
}
