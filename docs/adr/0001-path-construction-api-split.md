# 0001 — Path construction API — explicit segments vs parsed string

Date: 2026-05-18
Status: Accepted

## Context and Problem Statement

`Path` is the foundational key type for scopes, labels, and preference
resolution. Callers need two construction modes: building a path from
known domain segments at compile time, and parsing a raw string of unknown
format at runtime. Conflating these in one method forces a separator
assumption into the API contract.

## Decision Drivers

* Round-trip equality — `path.value()` must equal the original input;
  silent transformation breaks stored path strings
* Configurable separator — different harnesses may use different delimiters
* Strictness — malformed input should fail explicitly, not produce a
  silently wrong path

## Considered Options

* **Option A** — Single `Path.of(String)` factory, hardcoded `/` separator
* **Option B** — Single `Path.of(String)` with configurable separator injected at construction
* **Option C** — Two APIs: `Path.of(String...)` for explicit segments, `Path.parse(String)` with configurable PathParser strategy

## Decision Outcome

Chosen option: **Option C**, because it encodes the semantic distinction at
the type level. `of(String...)` is a constructor — the caller is responsible
for clean segments. `parse(String)` is an interpreter — the platform is
responsible for applying the configured separator.

### Positive Consequences

* Round-trip equality is guaranteed — `parse("a/b").value()` → `"a/b"`, no mutation
* Separator is installation-wide config (`casehub.platform.path.separator`),
  not per-call
* `PathParser` is a functional interface — custom parsers require no boilerplate

### Negative Consequences / Tradeoffs

* Two APIs to learn instead of one
* `Path.of("a/b")` is now a compile error — existing callers must be migrated

## Pros and Cons of the Options

### Option A — Single of(String), hardcoded /

* ✅ Simple, one method to learn
* ❌ Hardcodes `/` — unusable for dot-separated or other conventions
* ❌ Round-trip mutation possible if separator logic ever changes

### Option B — Single of(String), injected separator

* ✅ Configurable separator
* ❌ Separator at construction time — same path string parses differently
  depending on which separator was injected at the call site
* ❌ No distinction between "I know these segments" and "parse this string"

### Option C — of(String...) + parse(String) split

* ✅ Semantic clarity — construction vs parsing are different operations
* ✅ PathParser strategy is replaceable platform-wide
* ✅ Round-trip equality enforced by design
* ❌ Migration cost for callers using the old single-argument factory

## Links

* casehubio/platform#2 — v2 revision that introduced PathParser
* `casehub/parent — docs/protocols/casehub/platform-spi-contract.md`
