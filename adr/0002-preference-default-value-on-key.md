# 0002 — Preference default value location — key record vs Preferences interface

Date: 2026-05-18
Status: Accepted

## Context and Problem Statement

`Preferences.get(key)` returns null when a preference is not configured.
Callers need a fallback. The question is where the fallback lives: on the
`PreferenceKey<T>`, on the `Preferences` interface as a default method, or
as a convention on the Preference record itself.

## Decision Drivers

* Discoverability — the default should be obvious at the definition site,
  not scattered across call sites
* Compile-time safety — the default must be the correct type T, enforced
  by the compiler
* Minimal interface surface — `Preferences` should not grow per-type default methods

## Considered Options

* **Option A** — `T.DEFAULT` constant on each Preference record, no interface support
* **Option B** — `Preferences.getOrDefault(key, T fallback)` — caller supplies fallback each time
* **Option C** — `PreferenceKey<T>` carries `T defaultValue`; `Preferences.getOrDefault(key)` applies it

## Decision Outcome

Chosen option: **Option C**, because the default is a property of the key
definition, not a per-call decision. The key is defined once (as a static
constant on the Preference record); having it carry the default makes the
fallback visible at the point of definition and eliminates null checks at
every call site.

### Positive Consequences

* Default is colocated with the key — one place to read and change it
* `getOrDefault(key)` never returns null — call sites are null-free
* Compiler enforces `defaultValue` is the correct `T` at key definition time

### Negative Consequences / Tradeoffs

* `PreferenceKey` constructor gains a third argument — slightly more verbose
* The `DEFAULT` constant on the record is now duplicated in the key definition;
  by convention they should be the same instance

## Pros and Cons of the Options

### Option A — T.DEFAULT on record, no interface support

* ✅ Default lives on the record — discoverable
* ❌ Every call site must null-check and apply the default manually
* ❌ No compile-time guarantee that callers handle null

### Option B — getOrDefault(key, fallback) — caller supplies fallback

* ✅ Flexible — different call sites can use different fallbacks
* ❌ Default is scattered — each call site decides independently
* ❌ Inconsistency risk — same key, different defaults at different call sites

### Option C — defaultValue on PreferenceKey, getOrDefault(key) applies it

* ✅ Single source of truth for the default
* ✅ Null-free call sites — getOrDefault is idiomatic
* ✅ Consistent — same default wherever the key is used
* ❌ Constructor arity increases from 2 to 3

## Links

* casehubio/platform#1 — initial implementation
* casehubio/platform#2 — v2 revision that introduced defaultValue
* `docs/protocols/casehub/typed-preference-keys.md`
* `docs/protocols/casehub/platform-spi-contract.md` (Rule 3)
