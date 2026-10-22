/*
 * Copyright 2020-Present The Open Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.platform.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DurableCoordinationListener implements WorkflowExecutionCompletableListener {

  private final Map<String, CoordinationPrimitive> primitives = new ConcurrentHashMap<>();
  private final AtomicLong globalVersion = new AtomicLong(0);
  private final CoordinationStateStore store;
  private static final ObjectMapper MAPPER = JsonMapper.builder().build();
  private final PostPersistenceListener postPersistence = new PostPersistenceListener();

  public DurableCoordinationListener(CoordinationStateStore store) {
    this.store = store;
  }

  public void register(CoordinationPrimitive primitive) {
    primitives.put(primitive.name(), primitive);
  }

  @SuppressWarnings("unchecked")
  public <T extends CoordinationPrimitive> T primitive(String name) {
    return (T) primitives.get(name);
  }

  public long currentVersion() {
    return globalVersion.get();
  }

  public WorkflowExecutionCompletableListener postPersistenceListener() {
    return postPersistence;
  }

  @Override
  public int priority() {
    return 500;
  }

  @Override
  public CompletableFuture<?> onTaskStarted(TaskStartedEvent ev) {
    List<CompletableFuture<?>> futures = new ArrayList<>();
    for (CoordinationPrimitive p : primitives.values()) {
      if (p instanceof DurableSemaphore sem && sem.matches(ev.taskContext())) {
        futures.add(sem.acquire(ev.taskContext()));
      } else if (p instanceof DurableSignal sig && sig.matchesReceiver(ev.taskContext())) {
        futures.add(sig.await().thenApply(v -> null));
      } else if (p instanceof DurableChannel ch && ch.matchesConsumer(ev.taskContext())) {
        futures.add(ch.take(ev.taskContext()));
      } else if (p instanceof DurableChannel ch && ch.matchesProducer(ev.taskContext())) {
        futures.add(ch.put(ev.taskContext()));
      } else if (p instanceof DurableStateMachine sm && sm.matches(ev.taskContext())) {
        sm.transition(ev.taskContext());
      }
    }
    if (futures.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }

  @Override
  public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
    for (CoordinationPrimitive p : primitives.values()) {
      if (p instanceof DurableBarrier barrier && barrier.matches(ev.taskContext())) {
        barrier.recordArrival(ev.taskContext());
      }
      if (p instanceof DurableQuorum q && q.matches(ev.taskContext())) {
        q.complete(ev.taskContext());
      }
      if (p instanceof DurableSignal sig && sig.matchesSender(ev.taskContext())) {
        sig.send(ev.taskContext().taskName());
      }
      if (p instanceof DurableSagaTracker saga && saga.matches(ev.taskContext())) {
        saga.stepCompleted(ev.taskContext().taskName());
      }
    }
    saveToStore(ev.workflowContext());
    return CompletableFuture.completedFuture(null);
  }

  @SuppressWarnings("unchecked")
  public void restore(String instanceId) {
    store
        .load(instanceId)
        .ifPresent(
            json -> {
              try {
                Map<String, Object> map = MAPPER.readValue(json, Map.class);
                CoordinationState coordState = CoordinationState.fromMap(map);
                if (coordState.version() <= globalVersion.get()) {
                  return;
                }
                globalVersion.set(coordState.version());
                for (var entry : coordState.primitives().entrySet()) {
                  CoordinationPrimitive primitive = primitives.get(entry.getKey());
                  if (primitive != null) {
                    primitive.restore(entry.getValue());
                  }
                }
              } catch (Exception e) {
                // skip malformed state
              }
            });
  }

  private void saveToStore(WorkflowContextData workflowContext) {
    if (primitives.isEmpty()) {
      return;
    }
    String instanceId = workflowContext.instanceData().id();
    long version = globalVersion.incrementAndGet();
    Map<String, Map<String, Object>> serialized = new ConcurrentHashMap<>();
    for (var entry : primitives.entrySet()) {
      serialized.put(entry.getKey(), entry.getValue().serialize());
    }
    CoordinationState coordState = new CoordinationState(version, serialized);
    try {
      String json = MAPPER.writeValueAsString(coordState.toMap());
      store.save(instanceId, json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize coordination state", e);
    }
  }

  private class PostPersistenceListener implements WorkflowExecutionCompletableListener {

    @Override
    public int priority() {
      return 1500;
    }

    @Override
    public CompletableFuture<?> onTaskCompleted(TaskCompletedEvent ev) {
      List<CompletableFuture<?>> futures = new ArrayList<>();
      for (CoordinationPrimitive p : primitives.values()) {
        if (p instanceof DurableBarrier barrier && barrier.matches(ev.taskContext())) {
          futures.add(barrier.awaitRelease());
        } else if (p instanceof DurableSemaphore sem && sem.matches(ev.taskContext())) {
          sem.release(ev.taskContext());
        }
      }
      if (futures.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
  }
}
