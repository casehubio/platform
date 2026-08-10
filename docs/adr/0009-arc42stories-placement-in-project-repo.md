# 0009 — ARC42STORIES.MD lives in the project repo, not the workspace

Date: 2026-06-06
Status: Accepted

## Context and Problem Statement

`casehub-platform` uses a two-repo session structure: a workspace repo (methodology
artifacts) and a project repo (source code, ADRs). A stub `ARC42STORIES.MD` was
committed to the workspace root in error during bootstrapping. The question is where
the permanent architecture record should live.

## Decision Drivers

* `ARC42STORIES.MD` replaces `DESIGN.md` as the primary architecture record — DESIGN.md
  lives in the project repo, so its replacement should too
* The file must be accessible to contributors who clone only the project repo, not the
  companion workspace
* Protocol `PP-20260603-33c84c` establishes that ARC42STORIES.MD belongs in the project
  repo for all CaseHub components

## Considered Options

* **Option A** — Keep in workspace root (current state at branch open)
* **Option B** — Move to project repo root alongside DESIGN.md and CLAUDE.md

## Decision Outcome

Chosen option: **Option B**, because architecture documentation belongs with the code.
Contributors reading the source repo should be able to find the architecture record
without accessing the methodology workspace. The workspace holds ephemeral session
artifacts (handovers, plans, blog); permanent records belong in the project.

### Positive Consequences

* Architecture record is co-located with the code it describes
* Contributors cloning `casehubio/platform` find ARC42STORIES.MD at the repo root
* Consistent with DESIGN.md placement convention

### Negative Consequences / Tradeoffs

* Minor: the workspace stub must be explicitly deleted to avoid divergence
* The workspace DESIGN.md remains as a companion/redirect document (not retired)

## Pros and Cons of the Options

### Option A — Keep in workspace

* ✅ No migration required
* ❌ Architecture record invisible to contributors who clone only the project repo
* ❌ Violates the equivalence with DESIGN.md placement
* ❌ Violates protocol PP-20260603-33c84c

### Option B — Move to project repo root

* ✅ Co-located with source code
* ✅ Consistent with DESIGN.md and CLAUDE.md placement
* ✅ Discoverable by any project repo consumer
* ❌ Requires removing the erroneous workspace stub

## Links

* Protocol `PP-20260603-33c84c` — ARC42STORIES.MD lives in the project repo, not the workspace
* Closes casehubio/platform#56 (ARC42STORIES.MD stub was in workspace; moved and completed)
