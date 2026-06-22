# CloudEvent Conformance Fixes — #107, #108, #109

**Branch:** `issue-107-cloudevent-cleanup`
**Date:** 2026-06-22 (revised after three code review iterations)
**Covers:** casehubio/platform#107, #108, #109

---

## Context

Three conformance fixes surfaced during the casehubio/connectors#20 CloudEvent audit.
All are scoped to the `streams-*` modules and `platform-api`. The canonical reference is
GE-20260621-629712 — **seven rules** every CloudEvent producer in the platform must satisfy.

This spec audits three of those seven, producing code changes for two:

- **Rule 2 — audited, already conformant.** `EndpointDescriptor` enforces
  `Objects.requireNonNull(tenancyId)` at construction. Null tenancyId is impossible; the
  invariant is the conformance. No code change. See §#108 for full reasoning.
- **Rule 3 — implemented** (Poll and Camel only): `fireAsync()` aligned to `.exceptionally()`
  for fire-and-forget dispatch. Kafka and AMQP excluded — see §#108.
- **Rule 7 — implemented**: `datacontenttype` propagated via `STREAM_DATA_CONTENT_TYPE`
  descriptor property. See §#109.

---

## #107 — Remove cloudevents version pins from root pom

**What:** Remove three `<dependency>` entries from `<dependencyManagement>` in the root
`pom.xml`:
- `io.cloudevents:cloudevents-api`
- `io.cloudevents:cloudevents-core`
- `io.cloudevents:cloudevents-json-jackson`

**Why:** casehubio/parent#276 promoted these to the BOM at version `4.0.1`. The local
overrides are now redundant and will silently diverge if the parent BOM is ever updated.

**Verification:** `mvn dependency:resolve` confirms all three still resolve to `4.0.1`.

**Version mismatch handling:** If the resolved version after removal is not `4.0.1` (e.g.,
the parent BOM has already advanced to a newer version), treat this as a silent version
upgrade — stop, raise a separate issue for the version bump (it deserves its own commit
and changelog entry), and restore the pins until that issue is resolved.

**Tests:** None — pom-only change; build + resolve verification is the test.

---

## #108 — Rule 3 alignment: .whenComplete → .exceptionally() for Poll and Camel

### What changes

`PollStreamProcessor.pollAndFire()` and `CamelStreamProcessor.addRoute()` both use
`.whenComplete((e, t) -> { if (t != null) LOG.warn... })` for fire-and-forget CloudEvent
dispatch. GE rule 3 prescribes `.exceptionally(ex -> { LOG.warn...; return null; })`.

This is a one-line semantic swap per method. No intermediate builder variable needed —
that structural refactor belongs in #109 where the conditional `withDataContentType()`
forces it.

### Why Poll and Camel only

Kafka and AMQP chain `.thenCompose(ignored -> message.ack())` after `fireAsync()`.
The two patterns are NOT equivalent in that context:

- `.whenComplete()` propagates the exception — if `fireAsync()` fails, `thenCompose`
  skips its lambda, the exception surfaces to SmallRye, and the message is NOT acked
  (retried or dead-lettered). **Correct.**
- `.exceptionally()` swallows the exception and returns null — `thenCompose` fires,
  `message.ack()` runs, and the message is permanently consumed despite the CloudEvent
  dispatch having failed. **Silent message loss. Wrong.**

GE rule 3 applies in fire-and-forget contexts. It must not be applied where the
`CompletionStage` is part of a transactional ack chain. Kafka and AMQP stay as-is.

### On the tenancyId null guard — deliberately absent

`EndpointDescriptor` enforces `Objects.requireNonNull(tenancyId, "tenancyId")` in its
compact constructor. Null tenancyId is impossible by construction. Adding a null check on
a value that cannot be null is dead code — it violates the project norm ("don't add
validation for scenarios that can't happen") and cargo-cults GE rule 2 without
understanding what it is for.

GE rule 2 ("null-safe — extension omitted when null") exists because CDI domain events
genuinely can carry null tenancyId (system-level events with no tenant context). That
is not the case here. `EndpointDescriptor.tenancyId` is always non-null: the invariant
enforced at construction time **is** the conformance with rule 2. No guard needed.

### Tests

None required. The `.whenComplete` → `.exceptionally()` change produces identical
observable behaviour in fire-and-forget dispatch. Existing tests pass unchanged.

### Commit

```
fix(platform#108): poll/camel fireAsync: .whenComplete → .exceptionally() (fire-and-forget semantics)
```

---

## #109 — Add STREAM_DATA_CONTENT_TYPE to EndpointPropertyKeys

### New constant

```java
/**
 * Content type of the raw stream payload declared by the endpoint operator,
 * e.g. {@code "application/json"}, {@code "application/avro"},
 * {@code "application/octet-stream"}.
 *
 * <p>When present on an {@link EndpointDescriptor}, stream modules set
 * {@link io.cloudevents.core.builder.CloudEventBuilder#withDataContentType(String)}
 * to this value. When absent, {@code datacontenttype} is omitted from the CloudEvent —
 * the format is undeclared and consumers must determine it themselves.
 *
 * <p>GE rule 7 states "set {@code application/json} when data is ObjectMapper-serialised."
 * Stream processors do not serialise — they receive pre-serialised opaque bytes from
 * external systems. The format is operator-declared, not derivable from serialisation.
 * This key is the mechanism by which operators make that declaration explicit.
 *
 * <p><b>Scope:</b> applies to {@link EndpointProtocol#KAFKA},
 * {@link EndpointProtocol#AMQP}, {@link EndpointProtocol#HTTP}
 * ({@code streams-poll} only), and {@link EndpointProtocol#CAMEL}.
 *
 * <p><b>Not used by {@code streams-webhook}.</b> Webhook requests are structured
 * CloudEvents; their {@code datacontenttype} is preserved from the incoming event.
 *
 * <p><b>Note on naming:</b> this is an {@link EndpointDescriptor#properties()} key,
 * not a CloudEvent extension attribute name. CloudEvent extension names must be
 * lowercase letters and digits only (no hyphens). This key is scoped to
 * {@code EndpointDescriptor.properties()} exclusively and is never used as a
 * CloudEvent extension attribute.
 */
public static final String STREAM_DATA_CONTENT_TYPE = "stream-data-content-type";
```

### Propagation — all four buildCloudEvent() methods

Each `buildCloudEvent()` gains a conditional `withDataContentType()` call after
`.withData(body)`. The builder must become an intermediate variable to support the
conditional — this is the structural refactor introduced by #109, not #108.

**Kafka and AMQP — descriptor can be null:**
```java
CloudEventBuilder builder = CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withType(type)
    .withSource(...)
    .withTime(OffsetDateTime.now())
    .withData(body)
    .withExtension("tenancyid", effectiveTenancyId);  // always non-null (fallback chain)

String contentType = descriptor != null
    ? descriptor.properties().get(EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE)
    : null;
if (contentType != null) {
    builder = builder.withDataContentType(contentType);
}

return builder.build();
```

**Poll and Camel — descriptor always present, tenancyId always non-null:**
```java
CloudEventBuilder builder = CloudEventBuilder.v1()
    .withId(UUID.randomUUID().toString())
    .withType(type)
    .withSource(...)
    .withTime(OffsetDateTime.now())
    .withData(body)
    .withExtension("tenancyid", descriptor.tenancyId());

String contentType = descriptor.properties()
    .get(EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE);
if (contentType != null) {
    builder = builder.withDataContentType(contentType);
}

return builder.build();
```

### Unregistered path (Kafka/AMQP descriptor == null)

When `descriptor` is null (message from an unregistered topic/address), `contentType`
is null and `datacontenttype` is omitted. This is correct.

First-principles rationale:
1. The only defensible non-null default would be `application/octet-stream`. But Kafka
   and AMQP streams are overwhelmingly JSON or Avro in practice — declaring octet-stream
   would actively mislead consumers that pattern-match on `datacontenttype`.
2. Both "registered-but-no-property-set" and "unregistered" produce identical CloudEvent
   output (`datacontenttype` absent). Treating them differently creates asymmetric consumer
   behaviour for states the consumer cannot distinguish — a leaky abstraction.
3. Protocol-level content-type hints (Kafka record headers, AMQP message content-type
   property) are a legitimate future source but belong in a separate feature, not here.
4. The platform cannot manufacture knowledge it does not have. Silence is correct.

### Tests

**Content-type present and absent — all four processors:**
- `buildCloudEvent_withContentType_setsDataContentType()`: descriptor with
  `STREAM_DATA_CONTENT_TYPE = "application/json"` → `getDataContentType() == "application/json"`.
- `buildCloudEvent_withoutContentType_omitsDataContentType()`: descriptor without
  the property → `getDataContentType()` is null.

**Null descriptor — Kafka and AMQP only (reachable, naturally testable):**
- `buildCloudEvent_nullDescriptor_omitsDataContentType()`: null descriptor →
  `datacontenttype` absent.

Poll and Camel do not get a null-descriptor test — the path is unreachable (they
always pass a discovered descriptor). This asymmetry is deliberate.

### Commit

```
feat(platform#109): add STREAM_DATA_CONTENT_TYPE to EndpointPropertyKeys + propagate to stream processors
```

---

## Full commit sequence

```
fix(platform#107): remove cloudevents version pins from root pom — managed by parent BOM
fix(platform#108): poll/camel fireAsync: .whenComplete → .exceptionally() (fire-and-forget semantics)
feat(platform#109): add STREAM_DATA_CONTENT_TYPE to EndpointPropertyKeys + propagate to stream processors
```

---

## Explicitly out of scope

- Reading `content-type` from Kafka record headers or AMQP message-level properties —
  a legitimate future feature; raise as a separate issue.
- Null-guarding `tenancyId` in any stream processor — `EndpointDescriptor` makes null
  impossible; the invariant is the conformance. A guard would be dead code.
- Aligning Kafka/AMQP `fireAsync()` to `.exceptionally()` — would cause silent message
  loss (swallows exception before `thenCompose(message.ack())` runs).
- `streams-webhook` — already receives structured CloudEvents; `datacontenttype`
  preserved from the incoming event.
