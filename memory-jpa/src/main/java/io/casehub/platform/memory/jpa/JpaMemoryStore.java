package io.casehub.platform.memory.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class JpaMemoryStore implements CaseMemoryStore {

    @Inject CurrentPrincipal principal;
    @Inject MemoryJpaConfig config;
    @Inject EntityManager em;
    @Inject ObjectMapper objectMapper;

    @Override
    @Transactional(TxType.REQUIRED)
    public String store(MemoryInput input) {
        MemoryPermissions.assertTenant(input.tenantId(), principal);

        MemoryEntry entry = new MemoryEntry();
        entry.memoryId   = UUID.randomUUID().toString();
        entry.tenantId   = input.tenantId();
        entry.entityId   = input.entityId();
        entry.domain     = input.domain().name();
        entry.caseId     = input.caseId();
        entry.text       = input.text();
        entry.attributes = serializeAttributes(input.attributes());
        entry.createdAt  = Instant.now();

        MemoryEntry.persist(entry);
        return entry.memoryId;
    }

    @Override
    @Transactional(TxType.REQUIRED)
    public List<Memory> query(MemoryQuery query) {
        MemoryPermissions.assertTenant(query.tenantId(), principal);

        if (config.fts().enabled() && query.question() != null) {
            return queryFts(query);
        }
        return queryChronological(query);
    }

    private List<Memory> queryChronological(MemoryQuery query) {
        var jpql = new StringBuilder(
            "FROM MemoryEntry WHERE tenantId = :tenantId AND entityId = :entityId AND domain = :domain");
        if (query.caseId() != null) jpql.append(" AND caseId = :caseId");
        if (query.since()  != null) jpql.append(" AND createdAt >= :since");
        jpql.append(" ORDER BY createdAt DESC");

        var jq = em.createQuery(jpql.toString(), MemoryEntry.class)
            .setParameter("tenantId", query.tenantId())
            .setParameter("entityId", query.entityId())
            .setParameter("domain",   query.domain().name())
            .setMaxResults(query.limit());

        if (query.caseId() != null) jq.setParameter("caseId", query.caseId());
        if (query.since()  != null) jq.setParameter("since",  query.since());

        return jq.getResultList().stream().map(this::toMemory).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Memory> queryFts(MemoryQuery query) {
        var sql = new StringBuilder("""
            SELECT * FROM memory_entry
            WHERE tenant_id = :tenantId AND entity_id = :entityId AND domain = :domain
              AND to_tsvector(CAST(:lang AS regconfig), text)
                  @@ websearch_to_tsquery(CAST(:lang AS regconfig), :question)
            """);
        if (query.caseId() != null) sql.append("  AND case_id = :caseId\n");
        if (query.since()  != null) sql.append("  AND created_at >= :since\n");
        sql.append("""
            ORDER BY ts_rank(
                to_tsvector(CAST(:lang AS regconfig), text),
                websearch_to_tsquery(CAST(:lang AS regconfig), :question)
            ) DESC
            """);

        var nq = em.createNativeQuery(sql.toString(), MemoryEntry.class)
            .setParameter("tenantId", query.tenantId())
            .setParameter("entityId", query.entityId())
            .setParameter("domain",   query.domain().name())
            .setParameter("lang",     config.fts().language())
            .setParameter("question", query.question())
            .setMaxResults(query.limit());

        if (query.caseId() != null) nq.setParameter("caseId", query.caseId());
        if (query.since()  != null) nq.setParameter("since",  query.since());

        return ((List<MemoryEntry>) nq.getResultList()).stream().map(this::toMemory).toList();
    }

    @Override
    @Transactional(TxType.REQUIRED)
    public void erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal);

        var jpql = new StringBuilder(
            "DELETE FROM MemoryEntry WHERE tenantId = :tenantId AND entityId = :entityId AND domain = :domain");
        if (request.caseId() != null) jpql.append(" AND caseId = :caseId");

        var q = em.createQuery(jpql.toString())
            .setParameter("tenantId", request.tenantId())
            .setParameter("entityId", request.entityId())
            .setParameter("domain",   request.domain().name());
        if (request.caseId() != null) q.setParameter("caseId", request.caseId());

        q.executeUpdate();
        em.clear();
    }

    @Override
    @Transactional(TxType.REQUIRED)
    public void eraseById(String memoryId, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal);
        em.createQuery("DELETE FROM MemoryEntry WHERE memoryId = :id AND tenantId = :tenantId")
            .setParameter("id",       memoryId)
            .setParameter("tenantId", tenantId)
            .executeUpdate();
        em.clear();
    }

    @Override
    @Transactional(TxType.REQUIRED)
    public void eraseEntity(String entityId, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal);
        em.createQuery("DELETE FROM MemoryEntry WHERE tenantId = :tenantId AND entityId = :entityId")
            .setParameter("tenantId", tenantId)
            .setParameter("entityId", entityId)
            .executeUpdate();
        em.clear();
    }

    private Memory toMemory(MemoryEntry e) {
        return new Memory(
            e.memoryId,
            e.entityId,
            new MemoryDomain(e.domain),
            e.tenantId,
            e.caseId,
            e.text,
            deserializeAttributes(e.attributes),
            e.createdAt
        );
    }

    private String serializeAttributes(Map<String, String> attrs) {
        try {
            return objectMapper.writeValueAsString(attrs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize attributes", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeAttributes(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize attributes: " + json, e);
        }
    }
}
