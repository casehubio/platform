package io.casehub.platform.memory.jpa;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA entity for a stored memory. domain stored as String (MemoryDomain.name()). */
@Entity
@Table(name = "memory_entry")
public class MemoryEntry {

    @Id
    @Column(name = "memory_id", length = 36, nullable = false)
    public String memoryId;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "entity_id", nullable = false)
    public String entityId;

    @Column(name = "domain", nullable = false)
    public String domain;

    @Column(name = "case_id")
    public String caseId;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    public String text;

    /** JSON string serialized from Map<String,String> using Jackson ObjectMapper. */
    @Column(name = "attributes", nullable = false, columnDefinition = "TEXT")
    public String attributes;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "subject_type")
    public String subjectType;

    @Column(name = "confidence")
    public String confidence;

    @Column(name = "pleasure")
    public Double pleasure;

    @Column(name = "arousal")
    public Double arousal;

    @Column(name = "dominance")
    public Double dominance;

    @Column(name = "principal_id")
    public String principalId;

    @Column(name = "shared_with")
    public String sharedWith;
}
