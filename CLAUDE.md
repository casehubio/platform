# casehub-platform Workspace

**Name:** casehub-platform

**Physical path:** `/Users/mdproctor/claude/casehub/platform/CLAUDE.md`
**Symlinked at:** `/Users/mdproctor/claude/public/casehub/platform/CLAUDE.md`
**Project repo:** `/Users/mdproctor/claude/casehub/platform`
**Workspace:** `/Users/mdproctor/claude/public/casehub/platform`
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/platform` and `add-dir /Users/mdproctor/claude/public/casehub/platform` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| adr | `adr/` |
| write-blog | `blog/` |

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/platform`) — plans, blog, specs, snapshots, handover
- **Project repo** (`/Users/mdproctor/claude/casehub/platform`) — source code, ADRs

Never rely on CWD for git operations:
```bash
git -C /Users/mdproctor/claude/public/casehub/platform ...   # workspace artifacts
git -C /Users/mdproctor/claude/casehub/platform ...          # project artifacts
```

Two remotes are configured on the project repo:
- `origin` → `casehubio/platform` (canonical)
- `mdproctor` → `mdproctor/platform` (personal fork)

**Git hooks:** `.githooks/pre-push` is committed. Activate on each clone:
```bash
git config core.hooksPath .githooks
```

Push to both after squash or significant merges:
```bash
git push --force-with-lease origin main
git push --force mdproctor main   # --force on first push after fork creation
```

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `adr/` |
| protocols  | garden      | `/Users/mdproctor/claude/casehub/garden/docs/protocols/` — never create local protocol files |
| specs      | project     | lands in `docs/` |
| blog       | workspace   | staged; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | |
| snapshots  | workspace   | |
| handover   | workspace   | |

**Blog directory:** `/Users/mdproctor/claude/public/casehub/platform/blog/`

## Rules

- `platform-api/` must remain zero-dependency — no Quarkus, no JPA, no casehubio imports. Pure Java only.
- `platform/` contains Quarkus @DefaultBean implementations only — no domain logic
- Every SPI in platform-api gets a @DefaultBean implementation in platform
- Two @DefaultBean patterns exist:
  - **Configurable mock** (PreferenceProvider, CurrentPrincipal): returns values driven by @ConfigProperty — suitable when tests need to set specific return values
  - **Silent no-op** (CaseMemoryStore): always returns empty/void — suitable when the capability is optional and "not installed" means "nothing happens"
- `config/` reads YAML preference files at startup — declare as compile scope in production; not needed on test-only classpaths (mock handles test defaults via `application.properties`)

## Project Type

type: java

## Repository Role

Zero-dependency foundational SPIs and types shared across all casehub modules. Publishes before everything else in the build order.

**Build order position:** immediately after casehub-parent BOM. No casehubio dependencies.

**Consumers (do not modify these repos — raise issues instead):**
- casehub-ledger, casehub-work, casehub-qhorus, casehub-engine, claudony, devtown, aml, clinical

## Build Commands

```bash
mvn --batch-mode install
mvn --batch-mode deploy -DskipTests   # CI only — requires GITHUB_TOKEN
```

## Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `platform-api/` | `casehub-platform-api` | Pure Java SPIs — zero deps |
| `platform/` | `casehub-platform` | Quarkus @DefaultBean implementations (configurable mocks + no-ops) + `ReactiveCaseMemoryStore` interface + `BlockingToReactiveBridge` |
| `testing/` | `casehub-platform-testing` | @Alternative @Priority(1) identity fixtures — no Quarkus runtime |
| `config/` | `casehub-platform-config` | Scope-aware YAML + SmallRye Config PreferenceProvider — displaces mock when on classpath |
| `oidc/` | `casehub-platform-oidc` | @RequestScoped OIDC-backed CurrentPrincipal — reads actorId/groups from SecurityIdentity |
| `expression/` | `casehub-platform-expression` | JQ expression evaluation (JQEvaluator) |
| `persistence-jpa/` | `casehub-platform-persistence-jpa` | JPA-backed PreferenceProvider — scope-aware, hierarchy-resolved, current-only. Add as compile dep; consumers must add `classpath:db/platform/migration` to Flyway locations |
| `persistence-mongodb/` | `casehub-platform-persistence-mongodb` | MongoDB-backed PreferenceProvider — @Alternative @Priority(1), beats JPA when co-deployed. No Flyway; startup bean creates scope index |
| `memory-inmem/` | `casehub-platform-memory-inmem` | @Alternative @Priority(1) volatile CaseMemoryStore — ConcurrentHashMap, constructor-injected CurrentPrincipal, no quarkus:build goal. Add as test scope for @QuarkusTest isolation; compile for ephemeral installs. Do NOT combine with memory-jpa in production scope |
| `memory-jpa/` | `casehub-platform-memory-jpa` | @ApplicationScoped JPA CaseMemoryStore — PostgreSQL, Flyway V1000 (`classpath:db/memory/migration`), FTS via websearch_to_tsquery when question provided. No quarkus:build goal (CurrentPrincipal only on test classpath). Use @TestTransaction not @Transactional in tests |
| `scim/` | `casehub-platform-scim` | @ApplicationScoped SCIM 2.0 GroupMembershipProvider — displaces @DefaultBean mock. Auth: casehub.platform.scim.token (static) or quarkus.oidc-client.scim.* (client-credentials). @CacheResult on membersOf(). No quarkus:build goal |

## Package Structure (platform-api)

```
io.casehub.platform.api
  .path          — Path, hierarchical scope/label paths
  .preferences   — PreferenceProvider, Preferences, PreferenceKey<T> (carries defaultValue + parser),
                   SettingsScope, MapPreferences, Preference, SingleValuePreference, MultiValuePreference
  .identity      — CurrentPrincipal, GroupMembershipProvider
  .memory        — CaseMemoryStore (blocking SPI), MemoryDomain, MemoryInput, Memory,
                   MemoryQuery, EraseRequest, MemoryPermissions (static tenant assertion utility)
```

`platform/` also exposes `ReactiveCaseMemoryStore` (Mutiny SPI) and `BlockingToReactiveBridge @DefaultBean`
at `io.casehub.platform.memory`.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/platform
**Changelog:** GitHub Releases
