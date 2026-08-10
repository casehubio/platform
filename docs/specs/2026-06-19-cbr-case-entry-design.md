# CbrCaseEntry — Design Spec
**Issue:** casehubio/platform#87  
**Date:** 2026-06-19  
**Status:** Approved

## Problem

`CaseMemoryStore` has no standard schema for writing case-based reasoning cases. Consumers write ad-hoc attributes, making the Retain step of the CBR cycle inconsistent across callers. Without a standard schema, retrieval adapters (Mem0, Graphiti) cannot rely on any particular attribute layout.

## Design

### `CbrCaseEntry` record — `platform-api`, `io.casehub.platform.api.memory`

```java
public record CbrCaseEntry(
    String problem,    // required — MemoryInput.text (embedded for similarity search)
    String solution,   // required — MemoryAttributeKeys.SOLUTION attribute
    String outcome,    // nullable — MemoryAttributeKeys.OUTCOME attribute
    Double confidence  // nullable — [0.0, 1.0], MemoryAttributeKeys.CONFIDENCE attribute
)
```

**Validation (compact constructor):**
- `problem` and `solution`: non-null, non-blank
- `confidence`: null allowed; if non-null, must be in [0.0, 1.0]

**`toMemoryInput(entityId, domain, tenantId, caseId)`:**  
Maps fields to `MemoryInput`. `problem` → `text`. `solution`, `outcome` (if non-null), `confidence` (if non-null, formatted via `MemoryAttributeKeys.formatConfidence`) → attributes.

**`static from(Memory)`:**  
Extracts CBR fields. `text` → `problem`. Attributes → `solution`, `outcome`, `confidence` (null if absent). Parses confidence via `MemoryAttributeKeys.parseConfidence`.

### New constant: `MemoryAttributeKeys.SOLUTION = "solution"`

Joins the reserved cross-domain key set. Documents that the value is the natural language description of the action taken.

## Constraints

- Pure Java — no Quarkus, no JPA. Lives in `platform-api`.
- `problem` is the field embedded for semantic search; callers should write a descriptive natural language sentence, not a feature vector.
- `CbrCaseEntry` carries no context (entityId, domain, tenantId, caseId) — context is supplied at `toMemoryInput()` call time.

## Testing

- Construction validation: null/blank problem/solution throws; confidence bounds enforced.
- `toMemoryInput`: each field mapped to the right position/attribute key.
- `from(Memory)`: fields extracted correctly; absent attributes → null.
- Roundtrip: `from(toMemoryInput(...))` → equal entry.
