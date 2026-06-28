# Squash Plan — issue-55-agent-module

**Range:** `mdproctor/main..HEAD` (15 commits → 5 commits)
**Working branch:** `squash/wip-issue-55-agent-module-20260603-044213`
**Grouping strategy:** D (scope clustering — all commits share `platform#55` scope)

---

## Already Clean — 0 commits

All commits are candidates for compaction.

---

## Action Groups

---

### Group 1 — Spec document iterations
*Compaction group — 4 commits → 1*
**Final message:** `docs(platform#55): agent module design spec`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `20aa024` docs(platform#55): final spec revision with protocol fixes | ✅ KEEP | *(see Final message above)* |
| `30f4639` docs(platform#55): spec v3 — all v2 review findings resolved | 🔽 SQUASH ↑ | *(absorbed — spec iteration; spec v3 intermediate state)* |
| `60e1a7c` docs(platform#55): spec v4 — all v3 review findings resolved | 🔽 SQUASH ↑ | *(absorbed — spec iteration; spec v4 intermediate state)* |
| `97f428c` docs(platform#55): spec v5 — final pre-implementation fixes | 🔽 SQUASH ↑ | *(absorbed — spec iteration; spec v5 intermediate state)* |

> **Result:** 1 commit — final spec state only, three intermediate review versions absorbed.

---

### Group 2 — Root POM module registration
*Single commit — no action needed*

✅ KEEP `bb0ba98` build(platform#55): register agent-api and agent-claude modules in root POM

> **Result:** 1 commit — message adequate.

---

### Group 3 — casehub-platform-agent-api module
*Compaction group — 4 commits → 1*
**Final message:** `feat(platform#55): casehub-platform-agent-api — AgentProvider SPI, types, exceptions`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `b008aa7` feat(platform#55): add agent-api module — AgentProvider SPI + types | ✅ KEEP | *(see Final message above)* |
| `ce8771e` fix(platform#55): remove agent-claude config key from agent-api exception message | 🔽 SQUASH ↑ | *(absorbed — 2-line fix, agent-api layering correction)* |
| `15e0dd3` fix(platform#55): assertj version management, exception message, AgentProvider javadoc | 🔽 SQUASH ↑ | *(absorbed — < 20 lines, polish fixups for agent-api)* |
| `85e2a7d` fix(platform#55): defensive copies in AgentMcpServer records, remove FQN type ref | 🔽 SQUASH ↑ | *(absorbed — 13 lines, correctness fix for AgentMcpServer; moved from end of range to semantic home)* |

> **Result:** 1 commit — all agent-api fixes collapsed into the feature.
> Note: `85e2a7d` is chronologically the newest commit in the range but is moved earlier in the
> rebase todo to squash into its semantic home (b008aa7 introduces AgentMcpServer).

---

### Group 4 — platform/ NoOp + agent-claude/ scaffold
*Compaction group — 2 commits → 1 (MERGE)*
**Final message:** `feat(platform#55): NoOpAgentProvider @DefaultBean + agent-claude/ module scaffold`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `2ce034d` feat(platform#55): NoOpAgentProvider @DefaultBean in platform module | ✅ KEEP | *(see Final message above)* |
| `5568b4d` feat(platform#55): agent-claude scaffold — pom.xml + ClaudeAgentProperties | 🔀 MERGE ↑ | *(unified — both are setup commits for the same feature phase; touch different modules, same release unit)* |

> **Result:** 1 commit — NoOp default and agent-claude module setup merged as one release unit.

---

### Group 5 — ClaudeAgentClient + ClaudeAgentProvider + IT
*Compaction group — 4 commits → 1*
**Final message:** `feat(platform#55): ClaudeAgentClient @Startup + ClaudeAgentProvider — Claude SDK integration`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `158e685` feat(platform#55): ClaudeAgentClient — @Startup, semaphore, buildEventStream, TDD | ✅ KEEP | *(see Final message above)* |
| `12e6709` fix(platform#55): correlation logging on all lifecycle events, log level, test fix | 🔽 SQUASH ↑ | *(absorbed — code review fixups for ClaudeAgentClient; 41 lines)* |
| `2aaa6f3` feat(platform#55): ClaudeAgentProvider @ApplicationScoped | 🔽 SQUASH ↑ | *(absorbed — thin delegation class, 34 lines; part of same implementation unit)* |
| `c15997d` test(platform#55): gated integration test for ClaudeAgentClient | 🔽 SQUASH ↑ | *(absorbed — IT for same feature; 49 lines)* |

> **Result:** 1 commit — full ClaudeAgentClient/Provider/IT collapsed into one meaningful commit.

---

## AFTER — what `git log --oneline` will show

```
  15  commits (original)
  -10  absorbed by squash/merge
  ──────────────────────────────────────────────
   5  commits — no content lost

Simulated (from KEEP SHAs):
  158e685  feat(platform#55): ClaudeAgentClient @Startup + ClaudeAgentProvider
  2ce034d  feat(platform#55): NoOpAgentProvider @DefaultBean + agent-claude/ module scaffold
  b008aa7  feat(platform#55): casehub-platform-agent-api — AgentProvider SPI, types, exceptions
  bb0ba98  build(platform#55): register agent-api and agent-claude modules in root POM
  20aa024  docs(platform#55): agent module design spec
```

(Run `git log --oneline squash/wip-issue-55-agent-module-20260603-044213` after execution to verify)

---

**Rebase todo (for review):**

```
pick 20aa024  docs(platform#55): final spec revision with protocol fixes
squash 30f4639  docs(platform#55): spec v3
squash 60e1a7c  docs(platform#55): spec v4
squash 97f428c  docs(platform#55): spec v5
pick bb0ba98  build(platform#55): register agent-api and agent-claude modules in root POM
pick b008aa7  feat(platform#55): add agent-api module — AgentProvider SPI + types
squash ce8771e  fix(platform#55): remove agent-claude config key
squash 15e0dd3  fix(platform#55): assertj version management, exception message, AgentProvider javadoc
squash 85e2a7d  fix(platform#55): defensive copies in AgentMcpServer records, remove FQN type ref
pick 2ce034d  feat(platform#55): NoOpAgentProvider @DefaultBean in platform module
squash 5568b4d  feat(platform#55): agent-claude scaffold — pom.xml + ClaudeAgentProperties
pick 158e685  feat(platform#55): ClaudeAgentClient — @Startup, semaphore, buildEventStream, TDD
squash 12e6709  fix(platform#55): correlation logging on all lifecycle events, log level, test fix
squash 2aaa6f3  feat(platform#55): ClaudeAgentProvider @ApplicationScoped
squash c15997d  test(platform#55): gated integration test for ClaudeAgentClient
```

Note: `85e2a7d` is moved from its original position (newest) to immediately after its semantic
home (b008aa7 group). This is a rebase reordering — valid, no conflict expected since
85e2a7d only touches AgentMcpServer.java which is introduced in b008aa7.
