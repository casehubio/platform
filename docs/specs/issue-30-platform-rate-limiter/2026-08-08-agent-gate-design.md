# Agent Gate — Platform Rate Limiter for AgentProvider

**Issue:** casehubio/examples#30
**Branch:** issue-30-platform-rate-limiter
**Date:** 2026-08-08

## Problem

LLM API calls are expensive, rate-limited by providers, and slow. Multiple
callers compete for a shared pool of API capacity. Without platform-level
coordination:

- Individual apps reinvent rate limiting (manor has a concurrency gate,
  trellis has a sliding window in ActionService, future apps do something else)
- No consistency in behavior across the ecosystem
- Provider 429s surface as app errors rather than being handled transparently
- Cost control is per-app rather than centralized
- One runaway workload can starve others

## Solution

A CDI `@Decorator` module (`agent-gate/`) that transparently wraps any
`AgentProvider` with two orthogonal admission controls:

1. **Token bucket** — throughput control ("how many calls per unit time")
2. **Concurrency gate** — parallelism control ("how many calls in-flight simultaneously")

The decorator is activated by classpath presence (optional-module-pattern).
When unconfigured (all defaults zero), it is a pure passthrough with zero
overhead.

## Scope

### In scope (Phase 1 — this issue)

- Token bucket rate limiting with configurable rate and burst capacity
- Concurrency gate with blocking-with-timeout semantics
- Gate both `invoke()` and `openSession()`
- Per-query rate limiting within sessions
- New `AgentRateLimitException` in `agent-api`
- Configuration via SmallRye `@ConfigMapping`
- Required provider changes for semaphore subsumption:
  - `agent-claude/` `ClaudeAgentClient`: accept `max-concurrent-sessions=0`
    (always-permit `Semaphore(Integer.MAX_VALUE)` instead of throwing
    `IllegalStateException`)
  - `agent-langchain4j/` `ChatModelAgentProvider`: same validation change

### Out of scope (Phase 2 — issues #31, #32, #33)

- Token-aware (TPM) rate limiting (#31)
- Circuit breakers and fallback chains (#32)
- Per-tenant, per-model, per-workload rate limits (#33)
- Trellis ActionService migration (casehubio/examples#35 — child issue of #30;
  depends on this module being published to the platform artifact repository first)

## Architecture

### Module: `agent-gate/` (`casehub-platform-agent-gate`)

```
agent-gate/
├── pom.xml
└── src/
    ├── main/java/io/casehub/platform/agent/gate/
    │   ├── AgentGateProperties.java       # @ConfigMapping
    │   ├── TokenBucket.java                # Pure-Java, thread-safe, lazy refill
    │   ├── GatedAgentProvider.java          # CDI @Decorator
    │   └── GatedAgentSession.java           # Session wrapper
    └── test/java/io/casehub/platform/agent/gate/
        ├── TokenBucketTest.java
        ├── GatedAgentProviderTest.java
        └── GatedAgentSessionTest.java
```

### New type in `agent-api/`

```java
public class AgentRateLimitException extends RuntimeException {
    private final double permitsPerSecond;
    private final long retryAfterMillis;

    public AgentRateLimitException(double permitsPerSecond) {
        super("Agent rate limit exceeded (" + permitsPerSecond + " permits/sec)");
        this.permitsPerSecond = permitsPerSecond;
        this.retryAfterMillis = permitsPerSecond > 0
            ? (long) Math.ceil(1000.0 / permitsPerSecond)
            : 1000L;
    }

    public double permitsPerSecond() { return permitsPerSecond; }
    public long retryAfterMillis() { return retryAfterMillis; }
}
```

### Dependencies

- `casehub-platform-agent-api` (AgentProvider, AgentEvent, AgentSession)
- `quarkus-arc` (CDI decorator support)
- No platform-api, no governance, no domain types

### CDI Resolution Chain

```
Injection: AgentProvider
  → GatedAgentProvider (@Decorator @Priority(APPLICATION = 2000))
    → ClaudeAgentProvider (@Alternative @Priority(10))
      → NoOpAgentProvider (@DefaultBean)  ← displaced
```

Future decorators use `@Priority(APPLICATION + N)` to establish ordering
relative to the gate. In CDI, lower priority is called first — a decorator
at `APPLICATION - 10` runs before the gate (closer to caller), while
`APPLICATION + 10` runs after it (closer to bean).

The decorator wraps whichever implementation CDI resolves. Adding or removing
`agent-gate` from the classpath toggles decoration with zero code changes in
any consumer or provider.

### Relationship to Existing Backend Semaphores

Both `ClaudeAgentClient` and `ChatModelAgentProvider` have independent
`java.util.concurrent.Semaphore` instances with fail-fast `tryAcquire()`.
These were necessary before the gate existed — without the gate module, they
remain the only concurrency control.

When the gate module is on the classpath, the gate owns concurrency control
exclusively. Having both active creates three redundant limits on the same
call path where at most one is ever useful for any given configuration.

**Resolution:** Provider semaphore validation is updated to accept
`max-concurrent-sessions=0` (disabled — no semaphore created). The gate
module ships a `META-INF/microprofile-config.properties` (ordinal 200) that
sets both provider properties to `0`:

```properties
# Shipped in agent-gate module — disables provider semaphores when gate is active
casehub.platform.agent.claude.max-concurrent-sessions=0
casehub.platform.agent.langchain4j.max-concurrent-sessions=0
```

This gives single-point concurrency control:
- **Gate present:** gate's `max-concurrent` is the sole limit
- **Gate absent:** providers use their own defaults (unchanged behavior)
- **Override:** deployers can re-enable provider semaphores with ordinal 300+
  if they need a backend-specific hard ceiling above the gate's policy limit

Provider changes required:
- `ClaudeAgentClient`: validation changes from `if (maxSessions < 1) throw`
  to `if (maxSessions < 0) throw`; `maxSessions == 0` creates
  `new Semaphore(Integer.MAX_VALUE)` — an always-permit semaphore that
  requires zero changes to downstream code (`run()`, `openSession()`,
  `ClaudeAgentSession.close()`, all termination handlers)
- `ChatModelAgentProvider`: same validation change and always-permit
  semaphore — no changes needed in `ChatModelAgentSession`

## Configuration

```properties
# @ConfigMapping(prefix = "casehub.platform.agent.gate")
casehub.platform.agent.gate.max-concurrent=0           # 0 = no concurrency limit
casehub.platform.agent.gate.permits-per-second=0.0     # 0 = no rate limit
casehub.platform.agent.gate.burst-capacity=0           # 0 = auto (ceil(permitsPerSecond))
casehub.platform.agent.gate.acquire-timeout=30s        # max wait for call admission (invoke/openSession)
casehub.platform.agent.gate.query-acquire-timeout=5s   # max wait for per-query token (within sessions)
```

`acquire-timeout` controls gate admission only — it is separate from and
additive to the LLM call timeout (`AgentSessionConfig.timeout()` or
`ClaudeAgentProperties.defaultTimeout()`). A call with
`acquire-timeout=30s` and `AgentSessionConfig.timeout()=5m` may take up to
5m30s total: 30s waiting for admission, then 5m for the LLM response.

### Activation semantics

```
rateLimitActive  = permitsPerSecond > 0
concurrencyActive = maxConcurrent > 0
active           = rateLimitActive || concurrencyActive
```

When `active` is false (both zero — the default), the decorator is a pure
passthrough: `invoke()` and `openSession()` delegate directly with zero
overhead.

When only one control is active, only its admission step executes:
- `permits-per-second=2.0`, `max-concurrent=0` → token bucket check only,
  no concurrency gate (no `Semaphore` created)
- `permits-per-second=0.0`, `max-concurrent=5` → concurrency gate only,
  no token bucket (no `TokenBucket` created)

Construction validation:
- `burst-capacity > 0` requires `permits-per-second > 0` — a bucket with
  initial tokens but no refill rate would allow exactly `burst-capacity`
  calls then block forever. Throws `IllegalStateException` at startup.
- `max-concurrent < 0` or `permits-per-second < 0` → `IllegalStateException`

## Token Bucket Algorithm

Pure Java, thread-safe via fair `ReentrantLock` with `Condition`. Lazy refill
on access — no background refill thread.

- `storedPermits` (double) tracks available tokens with fractional accumulation
- `lastRefillNanos` tracks when refill was last computed
- `tryAcquire(Duration timeout) throws InterruptedException`: lock → refill
  from elapsed time → if token available, consume, `condition.signal()`,
  return true → if not, `condition.await(waitTime)` (releases lock, blocks
  until signalled or timeout) → on wakeup, refill and re-check → loop until
  success or deadline exceeded → return false on timeout
- `release()`: lock → `storedPermits = min(burstCapacity, storedPermits + 1)`
  → `condition.signal()` → unlock. Compensates when a consumed token must be
  refunded (e.g. concurrency admission fails after token consumption)
- Bucket starts full (burst capacity tokens at construction)
- Fair lock ensures FIFO ordering among waiting callers
- `Condition.await()` releases lock during wait — doesn't block other callers
- `Condition.signal()` (not `signalAll`) wakes exactly one waiter per token,
  eliminating thundering-herd contention under sustained overload

## Decorator Behavior

### `invoke()` flow

```
1. if (!active) → return delegate.invoke(config)       // passthrough
2. return Multi.createFrom().deferred(() -> {
     deadline = now + acquireTimeout
3.   if (rateLimitActive):
       TOKEN BUCKET: tokenBucket.tryAcquire(remaining(deadline))
       → false: throw AgentRateLimitException
       → InterruptedException: restore flag, throw RuntimeException(cause)
4.   if (concurrencyActive):
       CONCURRENCY GATE: gate.tryAcquire(remaining(deadline), MILLISECONDS)
       → false: tokenBucket.release() if rateLimitActive,
                throw AgentSessionLimitException
       → InterruptedException: tokenBucket.release() if rateLimitActive,
                               restore flag, throw RuntimeException(cause)
5.   delegate.invoke(config)
     → onTermination().invoke(gate::release)  // only if concurrencyActive
     catch → gate.release() if concurrencyActive, rethrow
   }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
```

Admission is deferred to subscription time via `Multi.createFrom().deferred()`
and shifted to the default worker pool via `runSubscriptionOn()`. This
preserves the cold Multi contract documented in `AgentProvider.invoke()`
Javadoc: "The returned Multi is cold — the agent session starts on
subscription." Event-loop callers are never blocked — the blocking admission
executes on a worker pool thread. If the caller never subscribes, no token or
concurrency slot is consumed.

Each step is conditional on its control being configured. When only rate
limiting is active, no concurrency gate exists. When only concurrency is
active, no token bucket exists. Token consumed first, then concurrency slot
acquired. Shared timeout budget.

### Token-first ordering with compensation

If concurrency admission fails at step 4 after a token was consumed at step 3,
the token is refunded via `tokenBucket.release()`. This ensures no wasted
throughput capacity from callers that never execute. The thundering-herd concern
with refunds does not apply here: refunded tokens re-enter the bucket and are
served FIFO via the fair lock's `Condition.signal()` — only one waiter is woken
per refunded token. Callers still must pass both the token bucket and
concurrency gate on retry, so the rate limit is not defeated.

If `delegate.invoke()` throws synchronously at step 5, the token is NOT
refunded — the delegate was called, so the attempt counts against the rate.
Similarly, if the delegate's Multi fails during execution, the token is
consumed. Rate limiting controls the rate of *admitted calls*, not *successful
completions*.

### `openSession()` flow

```
1. if (!active) → return delegate.openSession(init)
2. deadline = now + acquireTimeout
3. if (rateLimitActive):
     TOKEN BUCKET: tryAcquire(remaining(deadline))
     → false: throw AgentRateLimitException
     → InterruptedException: restore flag, throw RuntimeException(cause)
4. if (concurrencyActive):
     CONCURRENCY GATE: gate.tryAcquire(remaining(deadline), MILLISECONDS)
     → false: tokenBucket.release() if rateLimitActive,
              throw AgentSessionLimitException
     → InterruptedException: tokenBucket.release() if rateLimitActive,
                             restore flag, throw RuntimeException(cause)
5. delegate.openSession(init)
   → wrap in GatedAgentSession(session, tokenBucket, gate, queryAcquireTimeout,
                                rateLimitActive, concurrencyActive)
   catch → gate.release() if concurrencyActive, rethrow
```

Exceptions thrown directly (not via Multi) — matches existing `openSession()`
contract.

**Behavioral change from undecorated providers:** The existing
`AgentProvider.openSession()` Javadoc says "throws immediately if the
concurrent-session cap is reached." Without the gate, implementations use
fail-fast `semaphore.tryAcquire()`. With the gate, `openSession()` blocks up
to `acquireTimeout` before throwing — the gate provides admission queuing.
This is intentional: a brief concurrency spike no longer causes immediate
rejection. Since `openSession()` is already a blocking call (its `close()`
method blocks for drain), blocking during admission is consistent with the
method's contract. Callers must not call `openSession()` from Vert.x
event-loop threads — the same constraint as the existing `close()` method.

**Javadoc update required:** The `AgentProvider.openSession()` Javadoc
currently says `@throws AgentSessionLimitException immediately (not via
onFailure) if the concurrent-session cap is reached`. The word "immediately"
describes undecorated fail-fast behavior. With the gate active, admission
blocks up to `acquireTimeout`. The Javadoc must be updated to remove the
timing guarantee:

```java
@throws AgentSessionLimitException (not via onFailure) if concurrent-session
        admission fails — may block up to the configured admission timeout
        when a gate decorator is present
```

This is a deliverable of this issue, not deferred.

### `GatedAgentSession` behavior

```
query(prompt):
  1. if (rateLimitActive):
       tokenBucket.tryAcquire(queryAcquireTimeout)
       → false: return Multi.failure(AgentRateLimitException)
       → InterruptedException: restore flag, return Multi.failure
  2. return delegate.query(prompt)
  // No concurrency charge — session already holds a permit
  // queryAcquireTimeout (default 5s) is intentionally shorter than
  // acquireTimeout (default 30s) — mid-session waits consume the
  // session's wall-clock budget, so they should fail fast

close(maxWait):
  try { delegate.close(maxWait) }
  finally { if (concurrencyActive) gate.release() }

interrupt():
  → delegate.interrupt()   // passthrough
```

Per-query rate limiting prevents a long-lived session from circumventing
throughput control. The concurrency permit is held for the session lifetime
(acquired at `openSession()`, released at `close()`).

**Session leak risk:** An unclosed `GatedAgentSession` leaks the gate's
concurrency slot. Under the default configuration (gate present, provider
semaphores disabled via subsumption), this is the only slot lost — the impact
is equivalent to the pre-gate case of leaking a single provider semaphore
slot. If a deployer re-enables provider semaphores (ordinal 300+ override),
a leaked session loses both the gate slot and the provider slot. Callers must
use try-with-resources. Leak detection infrastructure (e.g. a `@Scheduled`
reaper that logs warnings for sessions held beyond a configurable duration)
is tracked as a separate enhancement (casehubio/examples#37).

## Exception Semantics

| Exception | Meaning | When | Retry guidance |
|-----------|---------|------|----------------|
| `AgentRateLimitException` | Too many calls per unit time (throughput) | Token bucket exhausted after timeout | `retryAfterMillis()` — `⌈1000/permitsPerSecond⌉` ms |
| `AgentSessionLimitException` | Too many concurrent calls (parallelism) | Concurrency gate full after timeout | No computed retry — depends on in-flight call duration |
| `AgentTimeoutException` | Call took too long (latency) | Existing — unchanged | N/A |

## Testing

Plain Java unit tests for algorithmic and behavioral verification. One
`@QuarkusTest` for CDI decorator integration (see CDI Integration Smoke Test
below).

### `TokenBucketTest`

- Burst: N tokens available at construction, N calls succeed, N+1 blocks
- Refill: after burst exhausted, calls succeed at configured rate
- Timeout: bucket empty + short timeout → returns false
- Concurrent access: multiple threads, total throughput matches configured rate
- Release: `release()` credits one token, capped at burst capacity
- Release wakes waiter: blocked `tryAcquire` succeeds after `release()` from another thread
- InterruptedException: interrupted thread gets InterruptedException, interrupt flag preserved

### `GatedAgentProviderTest`

- No-op passthrough: both limits zero → delegate called directly
- Concurrency-only: blocks excess, releases on stream termination
  (completion, failure, cancellation, synchronous throw)
- Rate-only: token bucket controls throughput
- Combined: both checks, shared timeout budget
- Token-first ordering: concurrency permit not held during rate-limit wait
- Token refund on concurrency failure: token returned, not wasted
- Deferred admission: `invoke()` returns cold Multi, admission on subscription
- `openSession()` gating: concurrency permit held until session close

### `GatedAgentSessionTest`

- Per-query rate limiting: each `query()` consumes a token
- Close releases concurrency permit and delegates to underlying session
- Rate limit failure on `query()` returns failure Multi, session stays open
- Interrupt passes through

### Provider Validation Changes

Existing tests in `ClaudeAgentClientTest` and `ChatModelAgentProviderTest`
assert that `maxConcurrentSessions == 0` throws `IllegalStateException`.
These must be updated to match the new validation boundary (`< 0` throws,
`== 0` creates `Semaphore(Integer.MAX_VALUE)`).

#### `ClaudeAgentClientTest` changes

- Update `constructor_zeroMaxConcurrentSessions_throwsIllegalState` → rename
  to `constructor_zeroMaxConcurrentSessions_createsAlwaysPermitSemaphore`:
  `maxSessions=0` creates client successfully, `availablePermits()` returns
  `Integer.MAX_VALUE`
- `constructor_negativeMaxConcurrentSessions_throwsIllegalState`: unchanged
  (negative still throws)
- Both constructors (`@Inject` and test) must be verified — the test
  constructor duplicates the validation logic

#### `ChatModelAgentProviderTest` changes

- Update `init_withZeroMaxConcurrentSessions_throws` → rename to
  `init_withZeroMaxConcurrentSessions_createsAlwaysPermitSemaphore`:
  `maxSessions=0` succeeds, `availablePermits()` returns `Integer.MAX_VALUE`
- `init_withNegativeMaxConcurrentSessions_throws`: unchanged

### CDI Integration Smoke Test

`@Decorator` is a new CDI pattern for this platform. Unit tests verify
internal logic but cannot verify CDI discovery, decorator priority resolution,
or config-driven activation. A single `@QuarkusTest` in `agent-gate/`
verifies the decorator activates correctly at CDI runtime:

#### `GatedAgentProviderCdiTest` (`@QuarkusTest`)

- Inject `AgentProvider` → verify it is wrapped by `GatedAgentProvider`
  (instanceof or behavioral check: configure `max-concurrent=1`, exhaust it,
  verify `AgentSessionLimitException` — this only works if the decorator is
  active)
- Config-driven passthrough: both limits zero → decorator present but
  passthrough, delegate called directly
- Verifies: Jandex discovery, `@Priority(APPLICATION)` resolution, config
  property injection

Uses a stub `AgentProvider` `@Alternative @Priority(1)` as the delegate bean.

Tests use stub `AgentProvider`/`AgentSession` implementations with
`CountDownLatch` and `AtomicInteger` for concurrency verification.

## Design Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| CDI pattern | `@Decorator` | Standard mechanism for cross-cutting behavior; zero consumer/provider changes |
| Gate `openSession()`? | Yes | Ungated sessions bypass rate control entirely |
| Per-query token consumption? | Yes | Each query costs money; session-open-only allows unlimited queries |
| Token bucket first, then concurrency + compensate | Yes | Minimizes time concurrency permits are held; `tokenBucket.release()` refunds on concurrency failure — no wasted throughput |
| Shared timeout budget | Yes | Total wait bounded by single `acquireTimeout` |
| New exception type | `AgentRateLimitException` | Throughput vs parallelism is a meaningful distinction |
| Default when unconfigured | No-op passthrough | Zero overhead unless explicitly configured |
| Bucket starts full | Yes | Allows initial burst without artificial warmup delay |
| Token bucket algorithm | Lazy refill, fair lock, `Condition.await`/`signal` | Condition variable eliminates thundering-herd; `signal()` wakes exactly one waiter per token |
| Deferred admission in `invoke()` | `deferred()` + `runSubscriptionOn(workerPool)` | Preserves cold Multi contract; never blocks event-loop callers; unsubscribed Multis consume no resources |
| Timeout independence | Gate ⊥ delegate | Total latency = admission-wait + delegate-timeout; gate has no visibility into delegate timeout |
| Split acquire timeout | `acquire-timeout` (30s) vs `query-acquire-timeout` (5s) | Call admission can wait longer; per-query waits consume session wall-clock budget and should fail fast |
| Provider semaphore ownership | Gate subsumes when present | Config override (ordinal 200) disables provider semaphores; single point of concurrency control eliminates redundant limits |

## Documentation Deliverables

Implementation updates ARC42STORIES.MD §5 (L8 container) and §7 (deployment
table) to include `agent-gate/`. No new chapter — this is an incremental
addition to L8: Agent Infrastructure within Journey J3, comparable in
significance to adding a new memory adapter to L6 (no chapter per adapter).

### CDI Pattern Protocol

`@Decorator` is a new CDI extension pattern for the CaseHub platform. The
existing protocol ecosystem (`alternative-extension-patterns.md`,
`ai-agent-provider-cdi-priority.md`) documents `@DefaultBean`, `@Alternative`,
and `@Priority` patterns but not decorators. The distinction:

- **`@Alternative`** — replaces an implementation (only one wins the priority contest)
- **`@Decorator`** — wraps an implementation (cross-cutting behavior that applies
  regardless of which implementation wins)

The gate establishes the precedent: decorators are for cross-cutting concerns
(rate limiting, circuit breaking, observability) that compose orthogonally to
the provider selection mechanism. Future decorators follow the same pattern:
`@Decorator @Priority(APPLICATION + N)` where N determines decorator ordering.

Protocol and ARC42STORIES updates tracked as casehubio/examples#36.

## Phase 2 Extension Points

The design accommodates future extensions without structural changes:

- **TPM (#31):** Add a second `TokenBucket` keyed by estimated token count,
  reconciled with actual usage from `InvocationComplete` events
- **Circuit breakers (#32):** Architecture (separate `@Decorator` or internal
  gate check) to be decided in #32; priority relative to the gate determines
  whether circuit state is checked before or after admission
- **Multi-tenant (#33):** Replace single `TokenBucket`/`Semaphore` with
  `Map<TenantKey, TokenBucket>` keyed by `(tenantId, model)` from config
