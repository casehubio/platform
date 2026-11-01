package io.casehub.platform.registration.jpa;

import java.io.Serializable;
import java.util.Objects;

public class PasswordCredentialEntityId implements Serializable {
    public String actorId;
    public String tenancyId;

    public PasswordCredentialEntityId() {}

    public PasswordCredentialEntityId(String actorId, String tenancyId) {
        this.actorId = actorId;
        this.tenancyId = tenancyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordCredentialEntityId that)) return false;
        return Objects.equals(actorId, that.actorId) && Objects.equals(tenancyId, that.tenancyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorId, tenancyId);
    }
}
