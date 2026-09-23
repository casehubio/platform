# Design: Merge 15 Spring Modules (#394)

## Problem

15 Spring modules are pure boilerplate — identical POM structure, zero or minimal hand-written Java. Each costs a Maven module (POM, directory, reactor entry, IntelliJ indexing, CI build step) for no architectural benefit.

## Solution

Merge into 3 targets:
- 10 `agent-*-spring` → 1 `agent-spring` (9 modules saved)
- 3 generated non-agent → absorbed into `platform-spring` (3 saved)
- 4 `streams-*-spring` → 1 `streams-spring` (3 saved)

### 1. spring-generator: per-module AutoConfiguration generation

**Current:** `SpringGeneratorMojo.execute()` loads one Jandex index, scans it, and generates one AutoConfiguration class with one `@ConditionalOnClass` anchor.

**Change:** When `<quarkusModules>` lists N modules, iterate and generate N AutoConfiguration classes — one per source module, each with its own `@ConditionalOnClass` guard. The resolve index (for type resolution) remains the composite of all modules + dependencies.

Per-module iteration in `execute()`:
```
for each module in resolveModules():
    scanIndex    = loadSingleIndex(module)
    resolveIndex = loadCompositeIndex()  // all modules + deps
    descriptors  = scanner.scan(scanIndex, resolveIndex)
    filter via ManualBeanScanner
    derive package from first descriptor's class
    derive className from module directory name
    generate AutoConfiguration via writer
    accumulate imports
write merged imports file
```

`deriveConfigClassName()` already works from a module name. The change is calling it per-module instead of once.

`AbstractGeneratorMojo.loadSingleIndex()` is currently private — make it package-private (or add a per-module iteration method).

### 2. Imports file merging

The generator reads any existing hand-written imports file from `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, then appends all generated class names. This eliminates the #393-style collision where hand-written shadows generated.

### 3. agent-spring module

**Dependencies (compile):**
- All 9 agent-*-core modules (claude, codex, config, gemini, gemini-cli, langchain4j, openai, router, runtime)
- agent-gate-core
- platform-api
- spring-boot-autoconfigure

**Dependencies (provided):**
- All 9 Quarkus agent modules (reactor ordering for Jandex)

**spring-generator config:**
```xml
<quarkusModules>
    <quarkusModule>${project.basedir}/../agent-claude</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-codex</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-config</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-gemini</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-gemini-cli</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-langchain4j</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-openai</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-router</quarkusModule>
    <quarkusModule>${project.basedir}/../agent-runtime</quarkusModule>
</quarkusModules>
```

**Hand-written code from agent-gate-spring:**
- `AgentGateBeanPostProcessor.java` → `agent-spring/src/main/java/io/casehub/platform/agent/gate/spring/`
- `AgentGateSpringProperties.java` → same package
- `AgentGateSpringAutoConfiguration.java` → same package
- `AgentGateBeanPostProcessorTest.java` → test mirror
- Hand-written imports entry for agent-gate's AutoConfiguration

### 4. streams-spring module

**Dependencies (compile):**
- streams-kafka-core, streams-amqp-core, streams-camel-core, streams-poll-core
- spring-boot-autoconfigure
- spring-kafka, spring-rabbit (optional for compile, required at runtime when activated)
- camel-spring-boot-starter (optional)

**Source files moved from 4 modules:**
- `KafkaStreamSpringAutoConfiguration.java`
- `AmqpStreamSpringAutoConfiguration.java`
- `CamelStreamSpringAutoConfiguration.java`
- `PollStreamSpringAutoConfiguration.java`

Hand-written imports file listing all 4.

No generator — all hand-written.

### 5. platform-spring absorption

Add expression, governance, and platform-view Quarkus modules to `<quarkusModules>`:
```xml
<quarkusModules>
    <quarkusModule>${project.basedir}/../platform</quarkusModule>
    <quarkusModule>${project.basedir}/../expression</quarkusModule>
    <quarkusModule>${project.basedir}/../governance</quarkusModule>
    <quarkusModule>${project.basedir}/../platform-view</quarkusModule>
</quarkusModules>
```

Add compile dependencies on expression-core, governance-core, platform-view-core.
Add provided dependencies on expression, governance, platform-view (Quarkus modules for Jandex).

### 6. Downstream updates

**spring-boot-starter:** Replace 17 individual references with:
- `casehub-platform-agent-spring` (replaces 10 agent-*-spring)
- `casehub-platform-streams-spring` (replaces 4 streams-*-spring, optional)
- Remove expression-spring, governance-spring, platform-view-spring (absorbed into platform-spring, already a dependency)

**spring-integration-test:** Update dependency references and exclusions.

**SpringParityRule exceptions:** Update baseline — 15 removed modules leave the exceptions list, agent-spring/streams-spring may need new entries or auto-detection.

**Parent POM:** Remove 15 `<module>` entries. Add `agent-spring` and `streams-spring`.

**Module directories:** Delete 15 directories after merge.

### 7. Verify mojo

`SpringVerifyMojo` extends `AbstractVerifyMojo`. It needs the same per-module iteration as the generate mojo — collect source types per module, verify against generated output. The verify composite index already handles multi-module via `loadCompositeIndex()`.

## Acceptance criteria

- [ ] spring-generator generates per-module AutoConfiguration when `<quarkusModules>` configured
- [ ] spring-generator merges hand-written + generated imports files
- [ ] agent-spring module replaces 10 individual agent-*-spring modules
- [ ] expression/governance/view-spring absorbed into platform-spring
- [ ] streams-spring module replaces 4 individual streams-*-spring modules
- [ ] All verify mojos pass
- [ ] spring-integration-test passes
- [ ] `mvn install` succeeds clean

## References

- dim6 audit report: `audit/dim6-complexity-reduction.md`
- #393 fix: `cb8c6524` (AutoConfiguration.imports collision)
- SpringGeneratorMojo.java — current single-module generation
- AbstractGeneratorMojo.java — already supports `quarkusModules` plural
- AutoConfigurationWriter.java — `selectAnchorType` picks one anchor per call
- ManualBeanScanner.java — hand-written bean exclusion
