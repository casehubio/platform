package io.casehub.platform.authn.webauthn.jpa;

import io.casehub.platform.api.authn.WebAuthnCredential;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "authn_webauthn_credential",
       indexes = {
               @Index(name = "idx_authn_webauthn_tenant_actor",
                      columnList = "tenancy_id, actor_id")
       })
public class WebAuthnCredentialEntity {

    @Id
    @Column(name = "credential_id", length = 500)
    public String credentialId;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "public_key_cose")
    public byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    public long signCount;

    @Column(name = "transports_json", columnDefinition = "TEXT")
    public String transportsJson;

    @Column(length = 100)
    public String aaguid;

    @Column(name = "display_name", length = 500)
    public String displayName;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    @Column(nullable = false)
    public boolean discoverable;

    public static WebAuthnCredentialEntity fromRecord(WebAuthnCredential record) {
        WebAuthnCredentialEntity entity = new WebAuthnCredentialEntity();
        entity.credentialId = record.credentialId();
        entity.actorId = record.actorId();
        entity.tenancyId = record.tenancyId();
        entity.publicKeyCose = record.publicKeyCose() != null
                ? Arrays.copyOf(record.publicKeyCose(), record.publicKeyCose().length) : null;
        entity.signCount = record.signCount();
        entity.transportsJson = record.transports().isEmpty() ? null
                : String.join(",", record.transports());
        entity.aaguid = record.aaguid();
        entity.displayName = record.displayName();
        entity.createdAt = record.createdAt();
        entity.lastUsedAt = record.lastUsedAt();
        entity.discoverable = record.discoverable();
        return entity;
    }

    public WebAuthnCredential toRecord() {
        return new WebAuthnCredential(
                credentialId,
                actorId,
                tenancyId,
                publicKeyCose != null ? Arrays.copyOf(publicKeyCose, publicKeyCose.length) : null,
                signCount,
                parseTransports(),
                aaguid,
                displayName,
                createdAt,
                lastUsedAt,
                discoverable
        );
    }

    private Set<String> parseTransports() {
        if (transportsJson == null || transportsJson.isBlank()) return Set.of();
        return Arrays.stream(transportsJson.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
