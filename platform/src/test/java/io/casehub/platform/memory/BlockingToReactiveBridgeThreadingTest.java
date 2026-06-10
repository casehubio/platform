package io.casehub.platform.memory;

import io.casehub.platform.api.memory.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that BlockingToReactiveBridge dispatches blocking delegate calls
 * to a worker thread, never running them on the subscribing thread.
 * This prevents event-loop blocking when the bridge is called from a reactive pipeline.
 */
class BlockingToReactiveBridgeThreadingTest {

    private static final MemoryDomain DOMAIN = new MemoryDomain("d");
    private static final String       TENANT = "t";
    private static final MemoryInput  INPUT  = new MemoryInput("e", DOMAIN, TENANT, null, "text", Map.of());
    private static final MemoryQuery  QUERY  = MemoryQuery.forEntity("e", DOMAIN, TENANT).withLimit(1);
    private static final EraseRequest ERASE  = new EraseRequest("e", DOMAIN, TENANT, null);

    private BlockingToReactiveBridge bridgeWith(AtomicLong capturedThreadId) {
        CaseMemoryStore spy = new CaseMemoryStore() {
            @Override public String store(MemoryInput i) {
                capturedThreadId.set(Thread.currentThread().getId());
                return "mem-1";
            }
            @Override public List<Memory> query(MemoryQuery q) {
                capturedThreadId.set(Thread.currentThread().getId());
                return List.of();
            }
            @Override public void erase(EraseRequest r) {
                capturedThreadId.set(Thread.currentThread().getId());
            }
            @Override public void eraseById(String id, String tid) {
                capturedThreadId.set(Thread.currentThread().getId());
            }
            @Override public int eraseEntity(String eid, String tid) {
                capturedThreadId.set(Thread.currentThread().getId());
                return 0;
            }
            @Override public List<String> storeAll(List<MemoryInput> inputs) {
                capturedThreadId.set(Thread.currentThread().getId());
                return inputs.stream().map(i -> "mem-batch").toList();
            }
        };
        var bridge = new BlockingToReactiveBridge();
        bridge.delegate = spy;
        return bridge;
    }

    @Test
    void store_executes_delegate_on_worker_thread() {
        // Initialize to caller thread ID — if delegate is never called, assertion still fails correctly.
        var capturedId = new AtomicLong(Thread.currentThread().getId());
        bridgeWith(capturedId).store(INPUT).await().indefinitely();
        assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
            "store() must offload delegate to a worker thread, not run on the subscribing thread");
    }

    @Test
    void query_executes_delegate_on_worker_thread() {
        var capturedId = new AtomicLong(Thread.currentThread().getId());
        bridgeWith(capturedId).query(QUERY).await().indefinitely();
        assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
            "query() must offload delegate to a worker thread, not run on the subscribing thread");
    }

    @Test
    void erase_executes_delegate_on_worker_thread() {
        var capturedId = new AtomicLong(Thread.currentThread().getId());
        bridgeWith(capturedId).erase(ERASE).await().indefinitely();
        assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
            "erase() must offload delegate to a worker thread, not run on the subscribing thread");
    }

    @Test
    void eraseById_executes_delegate_on_worker_thread() {
        var capturedId = new AtomicLong(Thread.currentThread().getId());
        bridgeWith(capturedId).eraseById("mem-1", TENANT).await().indefinitely();
        assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
            "eraseById() must offload delegate to a worker thread, not run on the subscribing thread");
    }

    @Test
    void eraseEntity_executes_delegate_on_worker_thread() {
        var capturedId = new AtomicLong(Thread.currentThread().getId());
        bridgeWith(capturedId).eraseEntity("entity-1", TENANT).await().indefinitely();
        assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
            "eraseEntity() must offload delegate to a worker thread, not run on the subscribing thread");
    }

    @Test
    void storeAll_executes_delegate_on_worker_thread() {
        var capturedId = new AtomicLong(Thread.currentThread().getId());
        var inputs = List.of(INPUT, new MemoryInput("e", DOMAIN, TENANT, null, "text2", Map.of()));
        var ids = bridgeWith(capturedId).storeAll(inputs).await().indefinitely();
        assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
            "storeAll() must offload delegate to a worker thread, not run on the subscribing thread");
        assertEquals(2, ids.size());
    }
}
