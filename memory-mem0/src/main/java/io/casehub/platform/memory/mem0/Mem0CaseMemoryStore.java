package io.casehub.platform.memory.mem0;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.*;
import io.casehub.platform.memory.mem0.dto.*;
import io.quarkus.arc.Arc;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Alternative
@Priority(1)
@ApplicationScoped
public class Mem0CaseMemoryStore implements CaseMemoryStore {

    @Override
    public java.util.Set<MemoryCapability> capabilities() {
        return java.util.Set.of(
            MemoryCapability.CHRONOLOGICAL_ORDER,
            MemoryCapability.DOMAIN_SCOPED,
            MemoryCapability.CASE_SCOPED,
            MemoryCapability.SINCE_FILTER,
            MemoryCapability.BATCH_STORE,
            MemoryCapability.SEMANTIC_SEARCH,
            MemoryCapability.ERASE_BY_ID,
            MemoryCapability.ERASE_ENTITY,
            MemoryCapability.ERASE_DOMAIN_CASE
        );
    }

    private static final Logger LOG = Logger.getLogger(Mem0CaseMemoryStore.class);

    static final String SEP = "::";

    @Inject @RestClient Mem0Client client;
    @Inject Mem0Config config;
    @Inject CurrentPrincipal principal;

    private boolean requestContextActive() {
        var c = Arc.container();
        return c == null || c.requestContext().isActive();
    }

    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "store"})
    @Override
    public String store(MemoryInput input) {
        MemoryPermissions.assertTenant(input.tenantId(), principal, requestContextActive());
        return sendAdd(input);
    }

    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "storeAll"})
    @Override
    public List<String> storeAll(List<MemoryInput> inputs) {
        if (inputs.isEmpty()) return List.of();
        inputs.forEach(i -> MemoryPermissions.assertTenant(i.tenantId(), principal));
        final var ids = new ArrayList<String>(inputs.size());
        for (final var input : inputs) {
            ids.add(sendAdd(input));
        }
        return List.copyOf(ids);
    }

    private String sendAdd(MemoryInput input) {
        final var request = new Mem0AddRequest(
            List.of(new Mem0AddRequest.Mem0Message("user", input.text())),
            compoundUserId(input.tenantId(), input.entityId()),
            input.domain().name(),
            input.caseId(),
            config.infer(),
            new HashMap<>(input.attributes())
        );
        final Mem0AddResponse response;
        try {
            response = client.add(request);
        } catch (WebApplicationException e) {
            throw toStoreException(e);
        }
        if (response.results() == null || response.results().isEmpty()) {
            throw new Mem0StoreException("store produced no result for: " + input.entityId());
        }
        return response.results().get(0).id();
    }

    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "query"})
    @Override
    public List<Memory> query(MemoryQuery query) {
        MemoryPermissions.assertTenant(query.tenantId(), principal, requestContextActive());

        final boolean relevanceWithQuestion =
            query.order() == MemoryOrder.RELEVANCE && query.question() != null;

        final List<Mem0Memory> all = new ArrayList<>();
        for (final String entityId : query.entityIds()) {
            all.addAll(fetchForEntity(query, entityId, relevanceWithQuestion));
        }

        final Instant barrier = query.since(); // null when no since filter

        // CHRONOLOGICAL: newest first. Also the fallback for RELEVANCE + null question.
        // For RELEVANCE + question: results are per-entity score-ordered (entity-order concat).
        // Scores are NOT cross-comparable across calls (variable max_possible in Mem0 scoring).
        final Comparator<Mem0Memory> order = relevanceWithQuestion
            ? null // preserve fan-out order; per-entity score ordering already applied by Mem0
            : Comparator.<Mem0Memory, Instant>comparing(m -> parseCreatedAt(m.createdAt()), Comparator.reverseOrder());

        final String tenantId = query.tenantId();
        var stream = all.stream()
            .filter(m -> barrier == null || isAfterSince(m, barrier));
        if (order != null) stream = stream.sorted(order);
        return stream
            .limit(query.limit())
            .map(m -> toMemory(m, tenantId))
            .collect(Collectors.toList());
    }

    private List<Mem0Memory> fetchForEntity(MemoryQuery query, String entityId, boolean search) {
        final String userId = compoundUserId(query.tenantId(), entityId);
        try {
            if (search) {
                final int topK = query.since() != null ? config.sinceSearchTopK() : query.limit();
                final var req = new Mem0SearchRequest(
                    query.question(), userId, query.domain().name(), query.caseId(),
                    topK, config.searchThreshold()
                );
                final Mem0ListResponse r = client.search(req);
                return r.results() != null ? r.results() : List.of();
            } else {
                final Mem0ListResponse r = client.list(userId, query.domain().name(), query.caseId());
                return r.results() != null ? r.results() : List.of();
            }
        } catch (WebApplicationException e) {
            throw toStoreException(e);
        }
    }

    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "erase"})
    @Override
    public void erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());
        try {
            client.deleteAll(
                compoundUserId(request.tenantId(), request.entityId()),
                request.domain().name(),
                request.caseId()
            );
        } catch (WebApplicationException e) {
            throw toStoreException(e);
        }
    }

    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "eraseById"})
    @Override
    public void eraseById(String memoryId, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal, requestContextActive());
        try {
            client.deleteById(memoryId);
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                return; // already absent — erasure is satisfied
            }
            throw toStoreException(e);
        }
    }

    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "eraseEntity"})
    @Override
    public void eraseEntity(String entityId, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal, requestContextActive());
        // No agent_id → all domains. No run_id → all cases. GDPR Art.17 wipe.
        try {
            client.deleteAll(compoundUserId(tenantId, entityId), null, null);
        } catch (WebApplicationException e) {
            throw toStoreException(e);
        }
    }

    // ── shared helpers ────────────────────────────────────────────────────────

    static String compoundUserId(String tenantId, String entityId) {
        return tenantId + SEP + entityId;
    }

    static String extractEntityId(String userId) {
        if (userId == null) return "";
        final int idx = userId.indexOf(SEP);
        return idx < 0 ? userId : userId.substring(idx + SEP.length());
    }

    private boolean isAfterSince(Mem0Memory m, Instant since) {
        if (m.createdAt() == null) return true;
        try {
            return !Instant.parse(m.createdAt()).isBefore(since);
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    private Instant parseCreatedAt(String s) {
        if (s == null) return Instant.EPOCH;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private Memory toMemory(Mem0Memory m, String tenantId) {
        return new Memory(
            m.id(),
            extractEntityId(m.userId()),
            new MemoryDomain(m.agentId() != null ? m.agentId() : ""),
            tenantId,
            m.runId(),
            m.memory() != null ? m.memory() : "",
            m.metadata() != null ? m.metadata() : Map.of(),
            parseCreatedAt(m.createdAt())
        );
    }

    private Mem0StoreException toStoreException(WebApplicationException e) {
        final int status = e.getResponse() != null ? e.getResponse().getStatus() : -1;
        String body = "";
        try {
            if (e.getResponse() != null) body = e.getResponse().readEntity(String.class);
        } catch (Exception bodyReadFailed) {
            LOG.debug("Could not read Mem0 error response body", bodyReadFailed);
        }
        return new Mem0StoreException(status, body, e);
    }
}
