# io.casehub.platform.agent.NoOpAgentSession

**Package:** `io.casehub.platform.agent`

**Kind:** `class`

No-op `AgentSession` returned by `NoOpAgentProvider.openSession`.
Enforces the state machine with no subprocess and no semaphore.
Package-private — callers interact through `AgentSession` only.

## Fields

### `state` (`java.util.concurrent.atomic.AtomicReference<io.casehub.platform.agent.NoOpAgentSession.State>`)

## Constructors

### `NoOpAgentSession()`

## Methods

### `public void close(java.time.Duration maxWait)`

#### Parameters

- `maxWait` (`java.time.Duration`)

### `public Uni<java.lang.Void> interrupt()`

### `public Multi<AgentEvent> query(java.lang.String prompt)`

#### Parameters

- `prompt` (`java.lang.String`)
