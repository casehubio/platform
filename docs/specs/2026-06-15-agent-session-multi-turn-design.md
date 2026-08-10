# Design: AgentSession Multi-Turn API (v2)

**Date:** 2026-06-15
**Issue:** casehubio/platform#58
**Branch:** `issue-58-agent-session-multi-turn`

---

## Context

`AgentProvider.invoke()` (platform#55) is single-shot: one system prompt, one user prompt, one streamed response. Multi-turn — sending follow-up prompts within the same Claude CLI subprocess — was explicitly deferred to this issue.

The `claude-code-sdk` `ClaudeAsyncClient` supports multi-turn natively: `connect(initialPrompt)` starts the session and sends the first prompt; `query(followUpPrompt)` sends subsequent prompts to the **same subprocess**, preserving context. No subprocess restart. No context loss.

This spec adds `AgentSession` to expose that capability at the platform SPI level.

---

## New Type: `AgentSessionInit` (in `agent-api/`)

Multi-turn sessions do not have a "user prompt" at session-open time — prompts come via `query()`. Using `AgentSessionConfig` (which requires `userPrompt`) for `openSession()` would create an uninhabited state: a required field with no valid value. Introduce a purpose-built type:

```java
public record AgentSessionInit(
        String systemPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    /** No MCP servers, default timeout, no correlation. */
    public static AgentSessionInit of(String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null);
    }
}
```

`AgentSessionConfig` is unchanged — `userPrompt` remains required.

---

## API Surface

### `AgentSession` — new interface in `agent-api/`

```java
public interface AgentSession extends AutoCloseable {

    /**
     * Send a query and stream the response.
     *
     * <p>Serial model — only one turn may be active at a time. Calling {@code query()}
     * while a turn is streaming throws {@link IllegalStateException}. Wait for the
     * current {@code Multi} to complete (or call {@link #interrupt()}) before calling again.
     *
     * <p>First call internally uses {@link ClaudeAsyncClient#connect(String)} to establish
     * the subprocess and send the initial prompt. Subsequent calls use
     * {@link ClaudeAsyncClient#query(String)}, preserving conversational context.
     *
     * <p><strong>Do not call from multiple threads concurrently.</strong> Concurrent calls
     * result in {@link IllegalStateException} for all but the first caller. In the unlikely
     * event of a write-before-CAS race (two threads both write {@code currentTurnFuture}
     * before either CAS), {@link #close(Duration)} may wait up to {@code maxWait} on an
     * uncompleted future before force-closing. The outcome is bounded and safe, but
     * callers must treat the session as single-threaded.
     *
     * @throws IllegalStateException if a turn is already active or the session is closed
     * @throws AgentTimeoutException via onFailure() if the wall-clock turn timeout is exceeded
     * @throws AgentProcessException via onFailure() if the subprocess errors
     */
    Multi<AgentEvent> query(String prompt);

    /**
     * Send an interrupt signal to the Claude CLI subprocess.
     *
     * <p>This is a best-effort fire-and-forget signal. The session remains in ACTIVE
     * state. If the Claude CLI responds to the interrupt by sending a {@code ResultMessage},
     * the current turn completes naturally and the session moves to IDLE, ready for the
     * next {@link #query(String)} call. If the CLI does not respond (e.g., mid-tool-call),
     * the turn continues as if interrupt was not sent.
     *
     * <p>The returned {@code Uni<Void>} completes when the signal is sent to the subprocess,
     * not when the Claude CLI responds. Callers must still observe the turn's {@code Multi}
     * for completion.
     *
     * <p>Calling from IDLE or CLOSED state is a no-op (returns immediately-completing Uni).
     * Only meaningful while ACTIVE.
     */
    Uni<Void> interrupt();

    /**
     * Close the session with a best-effort drain.
     *
     * <p>Sets the session state to CLOSED immediately (no new turns accepted). If a turn
     * is active, waits up to {@code maxWait} for it to complete naturally. After the wait
     * (whether the turn completed or not), terminates the subprocess via
     * {@link ClaudeAsyncClient#close()} and releases the concurrent-session semaphore.
     *
     * <p>The semaphore is released before subprocess termination completes. {@link
     * ClaudeAsyncClient#close()} schedules teardown on a bounded-elastic thread that blocks
     * up to 5 seconds for process exit — a new session may start while the previous
     * subprocess is still shutting down.
     *
     * <p>Idempotent — second call is a no-op.
     *
     * <p><strong>Sessions that are garbage-collected without {@code close()} leak a
     * semaphore slot permanently.</strong> Use try-with-resources.
     */
    void close(Duration maxWait);

    /** Delegates to {@code close(Duration.ofSeconds(30))}. */
    @Override
    default void close() {
        close(Duration.ofSeconds(30));
    }
}
```

### `AgentProvider` — new method

```java
public interface AgentProvider {

    Multi<AgentEvent> invoke(AgentSessionConfig config); // unchanged

    /**
     * Open a multi-turn session. The session holds a concurrent-session semaphore slot
     * for its lifetime — it must be closed by the caller on all paths (try-with-resources).
     *
     * <p>{@code init.systemPrompt} is configured at session-open time and transmitted
     * to the subprocess on the first {@link AgentSession#query(String)} call.
     * Session prompts are sent via {@link AgentSession#query(String)}.
     *
     * @throws AgentSessionLimitException immediately (not via onFailure) if the
     *         concurrent-session cap is reached
     */
    AgentSession openSession(AgentSessionInit init);
}
```

`NoOpAgentProvider` implements `openSession()` by returning a `NoOpAgentSession` (no subprocess, no semaphore).

---

## State Machine

Three states, enforced by `AtomicReference<State>` in `ClaudeAgentSession`:

```
IDLE      — session open; query() and interrupt() accepted
ACTIVE    — a turn is streaming; query() throws IllegalStateException
CLOSED    — terminal; query() throws, close() is no-op
```

State transitions:

| Event | From | To | Notes |
|-------|------|----|-------|
| `openSession()` succeeds | — | IDLE | Semaphore acquired |
| `query()` called | IDLE | ACTIVE | CAS; starts turn |
| Turn stream completes naturally | ACTIVE | IDLE | CAS; session reusable |
| Turn stream fails (subprocess error or timeout) | ACTIVE | CLOSED | CAS; semaphore released; subprocess closed |
| Turn stream cancelled (subscriber cancel) | ACTIVE | CLOSED | CAS; semaphore released; subprocess closed |
| `close()` called | IDLE | CLOSED | `getAndSet`; graceful; semaphore released |
| `close()` called | ACTIVE | CLOSED | `getAndSet`; waits up to maxWait for active turn; then terminates; semaphore released |
| `close()` called | CLOSED | CLOSED | No-op |
| `interrupt()` called | ACTIVE | ACTIVE | Fire-and-forget; state unchanged |
| `interrupt()` called | IDLE or CLOSED | — | No-op Uni |

**All ACTIVE → CLOSED transitions use CAS.** Exactly one of (subscriber cancellation, turn failure, `close()`) wins the ACTIVE→CLOSED transition. The winner releases the semaphore.

**All ACTIVE → IDLE transitions use CAS.** If `close()` already set CLOSED, the CAS in `onCompletion` fails silently — the CLOSED state is not overridden.

**Turn failure or timeout → CLOSED.** After any turn stream failure (including `AgentTimeoutException`), the subprocess is closed and the semaphore released by the `onFailure` handler. The session is not reusable.

**Cancellation → CLOSED.** Cancelling the `Multi` subscriber tears down the session entirely. To stop a turn without closing the session, use `interrupt()`.

**`close()` from ACTIVE:** Sets state CLOSED immediately (no new turns). Waits up to `maxWait` for the current turn to complete naturally (best-effort drain). After the wait, calls `sdkClient.close()` which calls `cleanup()`, which calls `turnSink.tryEmitComplete()` forcing the active turn to complete. Releases the semaphore. Note: the semaphore is released before subprocess termination finishes (teardown runs async).

---

## Implementation

### `ClaudeAgentSession` (package-private, in `agent-claude/`)

```java
class ClaudeAgentSession implements AgentSession {

    private final ClaudeAsyncClient sdkClient;
    private final Duration timeout;
    private final Semaphore semaphore;
    private final CopyOnWriteArraySet<ClaudeAsyncClient> activeSessions;
    private final ScheduledExecutorService timeoutScheduler;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    // Tracks whether sdkClient.connect() has been called yet.
    // false → first query() uses connect(); true → subsequent queries use query().
    private final AtomicBoolean sessionStarted = new AtomicBoolean(false);

    // Non-null in tests only — set by test constructor, null in production.
    // When non-null, query() delegates to this factory instead of sdkClient.
    // Production constructor: this.turnFactory = null;
    // Test constructor: this.turnFactory = turnFactory;
    private final Function<String, Multi<AgentEvent>> turnFactory;

    // Nullable; null disables all session-level correlation logging.
    // Passed in by the production constructor from AgentSessionInit.correlationId().
    // Test constructor does not pass it; null in test mode.
    // "Session opened" is logged in ClaudeAgentClient.openSession(), not here.
    @Nullable private final String correlationId;

    // Incremented after each successful IDLE→ACTIVE CAS (not on failed concurrent calls).
    // Used in turn-level log messages: "turn started [correlationId=X, turn=N]".
    private final AtomicInteger turnCounter = new AtomicInteger(0);

    // Written to volatile field BEFORE the IDLE→ACTIVE CAS; read by close() for drain wait.
    private volatile CompletableFuture<Void> currentTurnFuture;

    private volatile ScheduledFuture<?> currentTimeoutFuture;

    enum State { IDLE, ACTIVE, CLOSED }
}
```

**`query(String prompt)` implementation:**

```
1. Create pendingFuture = new CompletableFuture<>()
   this.currentTurnFuture = pendingFuture   ← volatile write BEFORE CAS
   // JMM: close()'s getAndSet(CLOSED) synchronizes-with query()'s CAS below,
   // which happens-after this volatile write — visibility of currentTurnFuture guaranteed.
   // If the CAS fails (session CLOSED), the pre-written future is harmless.

2. CAS(IDLE → ACTIVE); if fails:
   - state == ACTIVE → throw IllegalStateException("a turn is already active — wait for it to complete or call interrupt()")
   - state == CLOSED → throw IllegalStateException("session is closed")

3. AtomicBoolean timedOut = new AtomicBoolean(false)  // per-turn flag

4. Schedule wall-clock timeout:
   timeoutScheduler.schedule(() -> {
       if (timedOut.compareAndSet(false, true)) {
           if (sdkClient != null) sdkClient.close().subscribe();  // cleanup() → turnSink.tryEmitComplete()
           // sdkClient is null in test mode (turnFactory path) — timeout still fires timedOut
       }
   }, timeout.toMillis(), TimeUnit.MILLISECONDS)

5. Obtain turn stream:
   if (turnFactory != null):
       Multi<AgentEvent> stream = turnFactory.apply(prompt)   ← test path; sdkClient is null
   else:
       Flux<String> textFlux = sessionStarted.compareAndSet(false, true)
           ? sdkClient.connect(prompt).textStream()           ← first turn
           : sdkClient.query(prompt).textStream()             ← subsequent turns

6. Bridge Flux<String> → Multi<AgentEvent> (JdkFlowAdapter, same as ClaudeAgentClient.run())
   (test path: stream from step 5 is already Multi<AgentEvent>, no bridge needed)

7. Inject timeout-to-failure converter BEFORE exception typing and termination handlers:
   .onCompletion().call(() -> {
       if (timedOut.get()) {
           // SDK cleanup() called tryEmitComplete() — reinterpret as timeout failure
           return Uni.createFrom().failure(new AgentTimeoutException(timeout));
       }
       return Uni.createFrom().voidItem();
   })

8. Type exceptions (mirrors single-shot path; handlers in step 9 receive typed exceptions):
   .onFailure().transform(e ->
       e instanceof AgentTimeoutException ? e                 // already typed (from step 7)
       : timedOut.get() ? new AgentTimeoutException(timeout)  // timeout race: transport error arrived first
       : new AgentProcessException(
           Objects.toString(e.getMessage(), e.getClass().getSimpleName()), e))

9. Wire termination handlers (receive typed exceptions from step 8):
   onCompletion  → cancelTimeout();
                   pendingFuture.complete(null);
                   state.compareAndSet(ACTIVE, IDLE)  // CAS — if CLOSED wins, do not override

   onFailure     → cancelTimeout();
                   pendingFuture.complete(null);
                   if (state.compareAndSet(ACTIVE, CLOSED)) {
                       if (sdkClient != null) activeSessions.remove(sdkClient);
                       if (sdkClient != null) sdkClient.close().subscribe();  // idempotent if timeout already closed it
                       semaphore.release();
                   }
                   // Handles: AgentProcessException, AgentTimeoutException; test path: sdkClient null-guarded

   onCancellation → cancelTimeout();
                    pendingFuture.complete(null);
                    if (state.compareAndSet(ACTIVE, CLOSED)) {
                        if (sdkClient != null) activeSessions.remove(sdkClient);
                        if (sdkClient != null) sdkClient.close().subscribe();
                        semaphore.release();
                    }
                    // If CAS fails, close() already won — no double release

10. runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
```

**`interrupt()` → `Uni<Void>`:**

```
if state != ACTIVE → return Uni.createFrom().voidItem()  // no-op from IDLE or CLOSED
if sdkClient == null → return Uni.createFrom().voidItem()  // test mode: no subprocess to signal
return Uni.createFrom().publisher(
    JdkFlowAdapter.publisherToFlowPublisher(
        sdkClient.interrupt().flux()
    )
).onFailure().recoverWithNull()
// .recoverWithNull() silences IllegalStateException if close() races between
// the state check above and the Uni subscription (Uni is lazy).
// State remains ACTIVE; no timeout or semaphore changes.
```

**`close(Duration maxWait)` implementation:**

```
1. State prev = state.getAndSet(CLOSED)
   If prev == CLOSED → return (idempotent)

2. If prev == ACTIVE:
   CompletableFuture<Void> turnFuture = currentTurnFuture;  // volatile read
   if (turnFuture != null) {
       try {
           turnFuture.get(maxWait.toMillis(), TimeUnit.MILLISECONDS);
       } catch (TimeoutException | InterruptedException | ExecutionException ignored) {
           // waited as long as we could — proceeding to force close
       }
   }

3. cancelTimeout()
4. if (sdkClient != null) activeSessions.remove(sdkClient)
5. if (sdkClient != null) sdkClient.close().subscribe()
   // sdkClient is null in test mode (turnFactory path) — nothing to close.
   // Production: cleanup() → turnSink.tryEmitComplete() → onCompletion fires → CAS ACTIVE→IDLE fails
   //   (state is already CLOSED) → pendingFuture.complete(null) → harmless no-op
   // Subprocess teardown (process.waitFor(5s)) runs async on bounded-elastic thread.
6. semaphore.release()
   // Released BEFORE subprocess termination completes — a new session may open
   // while the previous subprocess shuts down (up to 5 seconds overlap).
```

### `ClaudeAgentClient.openSession()` — new method

```java
public AgentSession openSession(AgentSessionInit init) {
    // Semaphore check FIRST — mirrors run(). Factory check is inside the try block
    // so test-path sessions also decrement the semaphore and release it on close().
    if (!semaphore.tryAcquire()) {
        throw new AgentSessionLimitException(properties.maxConcurrentSessions());
    }
    try {
        if (streamFactory != null) {
            // test path — semaphore already acquired; session will release it on close()
            return new ClaudeAgentSession(
                properties, (prompt) -> Multi.createFrom().empty(),
                semaphore, activeSessions, timeoutScheduler);
        }

        Duration effectiveTimeout = init.timeout() != null
            ? init.timeout()
            : properties.defaultTimeout();

        ClaudeClient.AsyncSpec builder = ClaudeClient.async()
            .workingDirectory(Path.of(System.getProperty("user.dir")))
            .systemPrompt(init.systemPrompt());
        properties.binaryPath().ifPresent(builder::claudePath);
        Map<String, McpServerConfig> sdkMcpServers = toSdkMcpServers(init.mcpServers());
        if (!sdkMcpServers.isEmpty()) builder.mcpServers(sdkMcpServers);

        ClaudeAsyncClient sdkClient = builder.build();
        activeSessions.add(sdkClient);
        return new ClaudeAgentSession(sdkClient, effectiveTimeout, semaphore,
                                      activeSessions, timeoutScheduler, init.correlationId());
    } catch (Exception e) {
        semaphore.release();
        throw e;
    }
}
```

`openSession()` does not call `runSubscriptionOn()` — the session's `query()` applies it per turn.

### Correlation logging

If `init.correlationId()` is non-null, `ClaudeAgentSession` logs:
- `"Agent session opened [correlationId=X]"` — in `openSession()`, after the SDK client is built
- `"Agent session turn started [correlationId=X, turn=N]"` — at the start of each `query()`
- `"Agent session turn completed/failed/cancelled [correlationId=X, turn=N]"` — in the respective handler
- `"Agent session closed [correlationId=X]"` — in `close()`

Turn numbering is a simple `AtomicInteger` counter on `ClaudeAgentSession`.

---

## NoOp Implementation

`NoOpAgentSession` (in `platform/`, package-private) — enforces the state machine with no subprocess:

```java
class NoOpAgentSession implements AgentSession {
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    @Override
    public Multi<AgentEvent> query(String prompt) {
        if (!state.compareAndSet(State.IDLE, State.ACTIVE))
            throw new IllegalStateException(state.get() == State.CLOSED
                ? "session is closed"
                : "a turn is already active — wait for it to complete or call interrupt()");
        return Multi.createFrom().<AgentEvent>empty()
            .onCompletion().invoke(() -> state.compareAndSet(State.ACTIVE, State.IDLE));
    }

    @Override
    public Uni<Void> interrupt() {
        return Uni.createFrom().voidItem();  // no-op; no subprocess
    }

    @Override
    public void close(Duration maxWait) {
        // maxWait is ignored — no subprocess to drain
        state.set(State.CLOSED);
    }
}
```

`NoOpAgentProvider.openSession()` returns `new NoOpAgentSession()`. No semaphore.

---

## Tests

### `ClaudeAgentSessionTest` (plain JUnit5, no Quarkus)

Test constructor: `ClaudeAgentSession(ClaudeAgentProperties, Function<String, Multi<AgentEvent>> turnFactory, ...)`.
The `turnFactory` takes the prompt string and returns a `Multi<AgentEvent>` for that turn.
This allows tests to verify which prompt triggered which stream, and return different streams per prompt.

1. `query(prompt)` from IDLE → ACTIVE; concurrent `query()` throws `IllegalStateException("a turn is already active...")`
2. Turn completes → IDLE via CAS; next `query()` succeeds (session reusable)
3. Turn fails → CLOSED via CAS; semaphore released; subsequent `query()` throws `IllegalStateException("session is closed")`
4. Subscriber cancels mid-turn → CLOSED via CAS; `query()` after cancellation throws; `availablePermits()` restored
5. `close()` from IDLE: state = CLOSED; `availablePermits()` restored; no subprocess calls
6. `close()` idempotent: second call is no-op; `availablePermits()` decremented only once
7. `close()` from ACTIVE: drain waits for turn future; `close(shortTimeout)` returns within `shortTimeout + tolerance`; semaphore released
8. `interrupt()` from ACTIVE in test mode: no subprocess signal, state remains ACTIVE, no exception
9. `interrupt()` from IDLE or CLOSED: no-op, no exception

Note: timeout → `AgentTimeoutException` requires a real Claude subprocess and cannot be unit-tested via the factory. It belongs in `ClaudeAgentClientIT` (integration test, requires Claude binary), matching the single-shot path which also defers timeout coverage to IT.

### `ClaudeAgentClientOpenSessionTest` (plain JUnit5)

Tests `openSession()` via `ClaudeAgentClient` test constructor:

1. `openSession()` beyond semaphore limit throws `AgentSessionLimitException` immediately
2. Semaphore NOT released on turn completion (session-scoped, not turn-scoped)
3. Semaphore released after `close()`

### `NoOpAgentProviderTest`

Verifies `openSession()` returns a functional no-op session: state machine enforced, valid single-turn usage works, `interrupt()` is always a no-op.

---

## CLAUDE.md update

`agent-api/` package structure needs `AgentSession`, `AgentSessionInit`, and `AgentProvider.openSession()` added. Done as part of implementation docs step, not here.

---

## What This Does Not Cover

- **Crash recovery** — subprocess crash mid-session moves to CLOSED. Restart is the caller's responsibility. Known limitation.
- **Session resumption** — no mechanism to reconnect a CLOSED session to the same subprocess context.
- **Parallel multi-turn** — the serial model is intentional. Parallel turns to the same subprocess are undefined in the SDK.
- **`AgentEvent` extension** — `TextDelta` remains the only event type.
- **Interrupt reliability** — `interrupt()` is a fire-and-forget signal. Whether and when Claude CLI responds with a `ResultMessage` is determined by the CLI's internal state. There is no platform-level guarantee of ACTIVE→IDLE-via-interrupt; the turn may continue regardless. Callers relying on interrupt for flow control must also implement a fallback (e.g., `close()` after a timeout).
