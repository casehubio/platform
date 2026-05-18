# 0003 — Preferences lookup contract — null-returning get() + getOrDefault()

Date: 2026-05-18
Status: Accepted

## Context and Problem Statement

`Preferences` needs a lookup method. The API must handle the case where a
key is not configured. Three approaches: throw on absence, return Optional,
or return null with a companion getOrDefault.

## Decision Drivers

* Null safety at call sites — callers should not be forced to null-check
* Visibility of absence — "not configured" is a meaningful state, not an error
* MockPreferenceProvider behaviour — the mock returns null for typed get()
  because config strings cannot be cast to typed Preference instances

## Considered Options

* **Option A** — `get(key)` throws `NoSuchElementException` if absent
* **Option B** — `get(key)` returns `Optional<T>`
* **Option C** — `get(key)` returns null; `getOrDefault(key)` applies `key.defaultValue()`

## Decision Outcome

Chosen option: **Option C**, because "not configured" is a legitimate state
(the scope hierarchy may have no override), and `getOrDefault()` is the
idiomatic primary call site for all production use. `get()` is retained for
the few cases that genuinely need to distinguish "no override" from "default
applied" — specifically scope-walking implementations checking each level
before falling through to parent.

### Positive Consequences

* Production callers use `getOrDefault(key)` — never null
* Scope-walking implementations can use `get(key)` to detect absence cleanly
* Consistent with the Drools `OptionKey` precedent that validated this pattern at scale

### Negative Consequences / Tradeoffs

* `get(key)` returning null is surprising without reading the contract
* Requires discipline — all consumer code should default to `getOrDefault`

## Pros and Cons of the Options

### Option A — throws NoSuchElementException

* ✅ No null in the API
* ❌ Callers cannot distinguish "not configured" from "error" without try/catch
* ❌ Scope-walking implementations cannot check absence without exception handling

### Option B — returns Optional<T>

* ✅ Absence is explicit at the type level
* ❌ `Optional.map().orElse()` at every call site is verbose
* ❌ `MockPreferenceProvider` would return `Optional.empty()` for typed keys —
  correct but changes the interface contract significantly

### Option C — null-returning get() + getOrDefault()

* ✅ `getOrDefault(key)` is idiomatic and null-free for production callers
* ✅ `get(key)` supports scope-walking and absence detection without exceptions
* ✅ Consistent with Drools OptionKey pattern (validated at scale)
* ❌ `get()` returning null requires clear Javadoc contract

## Links

* casehubio/platform#1 — initial implementation
* casehubio/platform#2 — v2 revision that introduced getOrDefault
* `docs/protocols/casehub/platform-spi-contract.md` (Rule 3)
