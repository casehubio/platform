# Embedding Similarity Utility — Design Spec

**Issue:** casehubio/platform#134
**Date:** 2026-07-13
**Scale:** XS | **Complexity:** Low

## Problem

Cosine similarity is independently implemented in multiple repos:

- **engine** (`AgentEmbeddingProvider.cosineSimilarity`) — static method on an SPI interface, used by `SemanticAgentRoutingStrategy`
- **work** (`EmbeddingSkillMatcher`) — local implementation for skill-to-task matching
- **neocortex** (`EmbeddingTextSimilarity`) — delegates to LangChain4j's `CosineSimilarity.between(Embedding, Embedding)`

Each performs the same math: dot product divided by the product of magnitudes.

## Decision

Introduce a `Vectors` utility class in `platform-api` that provides cosine similarity and its building-block primitives. Engine and work replace their local implementations with the shared utility. Neocortex is not a consolidation candidate — it operates on LangChain4j `Embedding` types, not raw `float[]`.

## Design

### Location

`platform-api/src/main/java/io/casehub/platform/api/util/Vectors.java`

Same package as `UUIDv7`. Same pattern: `final` class, private constructor, static methods. Zero dependencies.

### API

```java
public final class Vectors {

    public static double dotProduct(float[] a, float[] b)

    public static double magnitude(float[] a)

    public static double cosineSimilarity(float[] a, float[] b)
}
```

### Contracts

All methods throw `NullPointerException` on null input (standard Java array dereference — no explicit null checks).

| Method | Input | Output | Edge cases |
|--------|-------|--------|------------|
| `dotProduct` | two `float[]`, same length | `double` — sum of element-wise products | Mismatched length → `IllegalArgumentException`. Empty arrays → 0.0 |
| `magnitude` | one `float[]` | `double` — L2 norm (√sum of squares) | Empty → 0.0 |
| `cosineSimilarity` | two `float[]`, same length | `double` in [-1, 1] | Zero vector(s) → 0.0 (sentinel, avoids NaN). Mismatched length → `IllegalArgumentException` |

### Implementation

`cosineSimilarity` uses a single fused loop computing dot product, magnitude-A, and magnitude-B in one pass. Each `float` operand is explicitly promoted to `double` before multiplication — `(double) a[i] * b[i]`, not `a[i] * b[i]` — so that products are computed at 64-bit precision. Without explicit promotion, `float × float` multiplies at 32-bit precision and only widens the result for accumulation, losing mantissa bits that compound across high-dimensional vectors (768+ dimensions for BGE-M3).

The public `dotProduct` and `magnitude` methods use the same explicit `(double)` promotion and exist for direct caller use but are not called internally by `cosineSimilarity`.

### Testing

`VectorsTest` in `platform-api/src/test/java/io/casehub/platform/api/util/`.

Coverage:

- **cosineSimilarity:** identical vectors → 1.0, opposite → -1.0, orthogonal → 0.0, zero vector(s) → 0.0, single-element, mismatched lengths → exception, empty arrays
- **dotProduct:** basic computation, mismatched lengths → exception, empty arrays → 0.0
- **magnitude:** basic computation, zero vector → 0.0, single element, empty → 0.0
- Floating-point tolerance via delta-based assertions

## Scope and closure

Issue #134 closes when the `Vectors` utility ships in `platform-api`. Consumer migration is tracked in separate issues below — #134 delivers the shared utility, not the consolidation of every call site.

## Consumer migration (out of scope for this issue)

After platform publishes:

- **engine:** remove `AgentEmbeddingProvider.cosineSimilarity()`, callers use `Vectors.cosineSimilarity()` — casehubio/engine#713
- **work:** remove local implementation in `EmbeddingSkillMatcher`, use `Vectors.cosineSimilarity()` — casehubio/work#302

### Behavioral changes on migration

The `Vectors` utility introduces two behavioral differences from the existing implementations that consumers should be aware of during migration:

1. **Length mismatch handling.** `Vectors` throws `IllegalArgumentException` on mismatched array lengths. The engine's current implementation has no length check (AIOOBE if `b` is shorter, silent truncation if `a` is shorter). Work's current implementation returns `0.0` silently. Mismatched embedding dimensions indicate a model configuration bug — the explicit exception is the correct behavior. Neither consumer should depend on the silent-mismatch behavior since embeddings from the same model always produce vectors of the same dimension.

2. **Precision.** `Vectors` uses explicit `(double)` promotion before multiplication. Work's current implementation uses `float`-precision multiplication, which loses mantissa bits for high-dimensional vectors. The engine already uses explicit `(double)` promotion. This is a correctness improvement for work, not a semantic change.

## Non-goals

- Euclidean distance, normalization, or other vector operations — add when a consumer needs them
- LangChain4j `Embedding` type overloads — neocortex already has LangChain4j's utility
- `double[]` overloads — all current consumers use `float[]` embeddings
