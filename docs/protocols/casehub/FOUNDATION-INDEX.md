# casehub Platform Protocol Index

Standing rules and principles for the casehub platform foundation.

## Protocols

| File | Rule | Applies To |
|------|------|------------|
| [no-conditional-tenancy-filtering.md](no-conditional-tenancy-filtering.md) | Never gate tenancy filtering on a deployment mode or feature flag | all modules — queries, events, cache keys, audit entries |
| [tenancy-repository-pattern.md](tenancy-repository-pattern.md) | Bind tenancyId inside data access classes — never at call sites | all modules — Repository pattern / data access layer |
