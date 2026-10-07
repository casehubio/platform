package io.casehub.platform.authn.session.jpa;

import io.casehub.platform.api.authn.SessionRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "authn_session",
       indexes = {
               @Index(name = "idx_authn_session_tenant_actor",
                      columnList = "tenancy_id, actor_id"),
               @Index(name = "idx_authn_session_expires",
                      columnList = "expires_at")
       })
public class SessionEntity {

    @Id
    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "groups_json", columnDefinition = "TEXT")
    public String groupsJson;

    @Column(name = "auth_method", nullable = false, length = 100)
    public String authMethod;

    @Column(name = "csrf_token")
    public String csrfToken;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "device_fingerprint", length = 500)
    public String deviceFingerprint;

    public static SessionEntity fromRecord(SessionRecord record) {
        SessionEntity e = new SessionEntity();
        e.sessionId = record.sessionId();
        e.actorId = record.actorId();
        e.tenancyId = record.tenancyId();
        e.groupsJson = groupsToString(record.groups());
        e.authMethod = record.authMethod();
        e.csrfToken = record.csrfToken();
        e.createdAt = record.createdAt();
        e.expiresAt = record.expiresAt();
        e.deviceFingerprint = record.deviceFingerprint();
        return e;
    }

    public SessionRecord toRecord() {
        return new SessionRecord(
                sessionId,
                actorId,
                tenancyId,
                stringToGroups(groupsJson),
                authMethod,
                csrfToken,
                createdAt,
                expiresAt,
                deviceFingerprint
        );
    }

    static String groupsToString(Set<String> groups) {
        if (groups == null || groups.isEmpty()) return null;
        return String.join(",", groups);
    }

    static Set<String> stringToGroups(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
