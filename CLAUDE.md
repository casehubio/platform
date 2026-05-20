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

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `adr/` |
| specs      | project     | lands in `docs/` |
| blog       | workspace   | staged; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | |
| snapshots  | workspace   | |
| handover   | workspace   | |

**Blog directory:** `/Users/mdproctor/claude/public/casehub/platform/blog/`

## Rules

- `platform-api/` must remain zero-dependency — no Quarkus, no JPA, no casehubio imports. Pure Java only.
- `platform/` contains Quarkus @DefaultBean mocks only — no domain logic
- Every SPI in platform-api gets a @DefaultBean mock in platform
- All @DefaultBean mocks must be configurable via @ConfigProperty
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
| `platform/` | `casehub-platform` | Quarkus @DefaultBean mocks |
| `testing/` | `casehub-platform-testing` | @Alternative @Priority(1) identity fixtures — no Quarkus runtime |
| `config/` | `casehub-platform-config` | Scope-aware YAML + SmallRye Config PreferenceProvider — displaces mock when on classpath |

## Package Structure (platform-api)

```
io.casehub.platform.api
  .path          — Path, hierarchical scope/label paths
  .preferences   — PreferenceProvider, Preferences, PreferenceKey<T> (carries defaultValue + parser),
                   SettingsScope, MapPreferences, Preference, SingleValuePreference, MultiValuePreference
  .identity      — CurrentPrincipal, GroupMembershipProvider
```

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/platform
**Changelog:** GitHub Releases
