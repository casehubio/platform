# Design: Qhorus Spring REST Controllers (#495)

Generate Spring MVC REST controllers for the casehub-qhorus repo by extracting
core POJOs from 9 JAX-RS @Path resources and wiring the rest-spring-generator
plugin. Same pattern as #494 (work), different repo.

## Context

casehub-qhorus has 10 @Path resources across 5 modules with no Spring MVC
equivalents. The rest-spring-generator Maven plugin scans @Path resources via
Jandex and generates @RestController classes delegating to a core POJO.

One resource (A2AResource) is excluded — it uses JAX-RS native SSE
(SseEventSink+Sse) and JSON-RPC dispatch, which the generator can't handle.

## Resources

| # | Resource | Module | Methods | Deps | Inner records | Notes |
|---|----------|--------|---------|------|--------------|-------|
| 1 | AgentCardResource | runtime | 3 GET | 6 | 0 | Agent card signing (optional via Instance) |
| 2 | CausalGraphResource | runtime | 2 GET | 4 | 0 | |
| 3 | ChannelResource | runtime | 20 | 14 | 14 | Largest — utility methods to extract |
| 4 | SpaceResource | runtime | 7 | 1 | 2 | Simplest runtime resource |
| 5 | ComplianceReportResource | compliance-report | 12 | 16 | 0 | Multipart upload excluded from core |
| 6 | ComplianceScheduleResource | compliance-report | 4 | 2 | 2 | Simple CRUD |
| 7 | ExternalAgentBindingResource | a2a-outbound | 5 | 3 | 0 | Already constructor-injected |
| 8 | SlackBindingResource | slack-channel | 3 | 7 | 0 | Already constructor-injected |
| 9 | WebhookRegistryResource | webhook-observer | 3 | 2 | 1 | Simplest overall |

**Excluded:** A2AResource (runtime) — JAX-RS native SSE, JSON-RPC dispatch.

## Architecture

### Core Extraction Pattern (same as #494)

Each @Path resource is split into two classes in the same module:

1. **Core POJO** (`.core` subpackage) — framework-neutral, constructor-injected,
   typed return values. Owns all business logic.

2. **JAX-RS resource** (existing class, refactored) — thin adapter. One
   `@Inject` field pointing to the core POJO. Each REST method delegates.

### CDI Wiring

Each module with extracted core POJOs gets `@Produces @ApplicationScoped`
producer methods. The spring-generator picks these up and generates `@Bean`
methods in the auto-configuration.

### Generator Pipeline

```
qhorus modules (5)      rest-spring-generator        qhorus-rest-spring
  @Path resources   -->   scan Jandex indexes    -->   generated
  + core POJOs            find delegate types          @RestController
                          generate controllers         classes
```

### New Module: qhorus-rest-spring

Mirrors `work-rest-spring`. Dependencies: the 5 qhorus modules containing
resources + spring-webmvc + jakarta.servlet-api. Plugin: rest-spring-generator
scanning 5 module directories.

### Existing Infrastructure

- `runtime-core/` — 60 service/domain POJOs (already exist, not REST delegates)
- `runtime-spring/` — 50 hand-written `@Bean` methods (commented out in parent pom)
- No rest-spring-generator configured anywhere yet

### Special Handling

**ComplianceReportResource:** 16 deps, content negotiation (JSON/CSV/HTML/PDF),
`@RestForm FileUpload` for multipart upload. The multipart upload method is
excluded from core extraction — all other 11 methods extract normally. The
remaining method can be hand-written in qhorus-rest-spring if needed.

**ChannelResource:** 20 methods, 14 deps, 14 inner records. Inner records
extracted to `.core` subpackage as top-level classes. Utility methods
(resolve, parseTypes, parseLongParam) moved into the core POJO.
ServerExceptionMapper generated as @ControllerAdvice by the provider scanner.

**ExternalAgentBindingResource / SlackBindingResource:** Already use
constructor injection — minimal extraction needed, mostly moving business
logic to `.core` package and keeping the resource as a thin delegate.

## Verification

After implementation, `mvn generate-sources` in qhorus-rest-spring should
produce 9 controllers + exception advices. `mvn verify` drift detection
ensures controllers stay in sync with Quarkus sources.

## References

- #494 design spec (`2026-09-20-work-rest-spring-controllers-design.md`)
- D44 in decisions.md
- D39 (same-module extraction), D40 (mirror pattern)
- work-rest-spring/pom.xml — reference generator configuration
- rest-spring-generator source — RestResourceScanner, RestControllerWriter, ProviderScanner
- qhorus resource survey (this session)
- GitHub casehubio/parent#495
