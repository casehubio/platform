package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.InvitationRecord;
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
@Table(name = "registration_invitation",
       indexes = {
               @Index(name = "idx_reg_invite_token", columnList = "token_hash", unique = true),
               @Index(name = "idx_reg_invite_tenant", columnList = "tenancy_id")
       })
public class InvitationEntity {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "token_hash", nullable = false, length = 64)
    public String tokenHash;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "assigned_groups", columnDefinition = "TEXT")
    public String assignedGroups;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "created_by", nullable = false)
    public String createdBy;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "consumed", nullable = false)
    public boolean consumed;

    public static InvitationEntity fromRecord(InvitationRecord record) {
        InvitationEntity e = new InvitationEntity();
        e.id = record.id();
        e.tokenHash = record.tokenHash();
        e.email = record.email();
        e.tenancyId = record.tenancyId();
        e.assignedGroups = groupsToString(record.assignedGroups());
        e.expiresAt = record.expiresAt();
        e.createdBy = record.createdBy();
        e.createdAt = record.createdAt();
        e.consumed = record.consumed();
        return e;
    }

    public InvitationRecord toRecord() {
        return new InvitationRecord(
                id, tokenHash, email, tenancyId,
                stringToGroups(assignedGroups),
                expiresAt, createdBy, createdAt, consumed);
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
