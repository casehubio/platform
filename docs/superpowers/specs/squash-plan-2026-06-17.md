# Squash Plan — main — 2026-06-17

Range: `origin/main..HEAD` (19 commits → 3)

---

## Already Clean — 0 commits

All commits are being compacted into the three groups below.

---

## Group 1 — Module scaffold, config, design spec, docs sync
*Compaction group — 10 commits → 1*
**Final message:** `feat(platform#100): scaffold agent-claude-langchain4j — ClaudeAgentLangchain4jProperties, langchain4j-core dep, design spec`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `f7dbc75` feat(platform#100): scaffold agent-claude-langchain4j module | ✅ KEEP | *(see Final message above)* |
| `ed028a4` feat(platform#100): ClaudeAgentLangchain4jProperties config interface | 🔀 MERGE ↑ | *(unified — part of module setup; config interface is part of the scaffold story)* |
| `d99b13a` docs(platform#100): design spec — ChatModel adapter backed by AgentSession | 🔽 SQUASH ↑ | *(absorbed — pre-implementation planning doc; 7-round spec iteration follows)* |
| `09c7f08` docs(platform#100): revise spec — feedback round 1 | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `279e275` docs(platform#100): revise spec — feedback round 2 | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `9a63305` docs(platform#100): revise spec — feedback round 3 | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `e369615` docs(platform#100): revise spec — feedback round 4 | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `1abb46f` docs(platform#100): revise spec — feedback round 5 | 🔽 SQUASH ↑ | *(absorbed — spec iteration noise)* |
| `bef8c46` docs(platform#100): add agent-claude-langchain4j to CLAUDE.md module table | 🔽 SQUASH ↑ | *(absorbed — docs follow-on, stale-ref class)* |
| `27c7a41` docs: sync ARC42STORIES.MD — add agent-claude-langchain4j to L8 layer | 🔽 SQUASH ↑ | *(absorbed — docs follow-on, stale-ref class)* |

> **Result:** 1 commit. Closes #100 (primary issue).

---

## Group 2 — AgentSessionChatModel
*Compaction group — 3 commits → 1*

✅ KEEP `b7a1813` feat(platform#100): AgentSessionChatModel + tests — multi-turn ChatModel/StreamingChatModel wrapper
> Absorbed: `c104103` test: add multimodal + timeout propagation tests; `7680df3` fix: restore mockito-core test dep + quality cleanup

> **Result:** 1 commit.

---

## Group 3 — ClaudeAgentChatModel + post-implementation spec/test
*Compaction group — 6 commits → 1*

✅ KEEP `8a4e7b2` feat(platform#100): ClaudeAgentChatModel + tests — @Alternative @Priority(10) stateless ChatModel/StreamingChatModel adapter
> Absorbed: `f7452f6` test: add missing tests; `26efaff` refactor: code quality cleanup; `e986976` test: add 3 missing tests from code review; `caaea08` docs: sync spec to implementation; `e2fee74` docs: spec final

> **Result:** 1 commit.

---

## AFTER — what `git log --oneline` will show

```
  19  commits (original)
   0  pruned by filter-repo
  16  absorbed by squash
  ─────────────────────────────────────────────
   3  commits — no content lost

Sample:
  <sha>  feat(platform#100): ClaudeAgentChatModel — @Alternative @Priority(10) stateless ChatModel/StreamingChatModel adapter
  <sha>  feat(platform#100): AgentSessionChatModel — multi-turn ChatModel/StreamingChatModel wrapper backed by AgentSession
  <sha>  feat(platform#100): scaffold agent-claude-langchain4j — ClaudeAgentLangchain4jProperties, langchain4j-core dep, design spec
```
