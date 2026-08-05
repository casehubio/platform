# io.casehub.platform.agent.NoOpAgentProvider

**Package:** `io.casehub.platform.agent`

**Kind:** `class`

No-op `AgentProvider` active when `casehub-platform-agent-claude` is not
on the classpath. Returns an empty completed stream.

<p>A `WARN` log line is emitted on each invocation to make dev misconfiguration
immediately visible. The real agent and the NoOp both produce an empty completed Multi;
the log is the only observable distinction.

## Fields

### `LOG` (`Logger`)

## Constructors

### `public NoOpAgentProvider()`

## Methods

### `public Multi<AgentEvent> invoke(AgentSessionConfig config)`

#### Parameters

- `config` (`AgentSessionConfig`)

### `public AgentSession openSession(AgentSessionInit init)`

#### Parameters

- `init` (`AgentSessionInit`)
