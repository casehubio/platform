## D1: Inclusion strategy — opt-in vs auto-include

**Choice:** Opt-in via customModules varargs
**Alternatives:**
- Auto-include in PlatformSchemaGenerator — would change schema output for existing consumers with sealed types
- Auto-include with opt-out flag — complex API for questionable benefit
**Rationale:** Backwards-compatible. No schema output changes for existing consumers. Matches the issue's "Engine can optionally adopt it" language. Available in module/ package alongside EnumInliningModule and UnevaluatedPropertiesModule.
**Trade-offs:** Consumers must explicitly pass `new SealedHierarchyModule()` — slightly more boilerplate per consumer.
**Sources:** PlatformSchemaGenerator.java constructor pattern, issue #279 acceptance criteria
**Exploration:** quick
**Status:** captured

## D2: Discriminator property name

**Choice:** Hardcoded to "type"
**Alternatives:**
- Configurable discriminatorProperty parameter — adds constructor complexity for no known use case
**Rationale:** "type" is the JSON Schema / OpenAPI convention. All current consumers (neocortex with TemporalMark, CbrFilter, etc. and blocks with ~35 sealed interfaces) use "type". No consumer has expressed a need for a different discriminator field name.
**Trade-offs:** If a future consumer needs a different discriminator property name, the module would need modification.
**Sources:** neocortex SealedHierarchyModule.java line 53, JSON Schema discriminator conventions
**Exploration:** quick
**Status:** captured

## D3: Neocortex migration scope

**Choice:** Follow-up issue on casehubio/neocortex
**Depends on:** D1 (opt-in means neocortex explicitly adds the shared module)
**Alternatives:**
- Cross-repo branch touching both platform and neocortex — couples the release
**Rationale:** Platform publishes first, then consumers adopt. Cleaner scope — this branch does one thing: promote the module to the shared location.
**Trade-offs:** Neocortex will temporarily have two copies of the module until migration is completed.
**Sources:** issue #279 acceptance criteria, build order (platform publishes before neocortex)
**Exploration:** quick
**Status:** captured
