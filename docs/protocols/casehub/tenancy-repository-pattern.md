---
id: PP-20260520-e6a5f0
title: "Bind tenancyId inside data access classes — never at call sites"
type: rule
scope: platform
applies_to: "all modules — any class following the Repository pattern (data access layer)"
severity: critical
refs:
  - docs/protocols/casehub/no-conditional-tenancy-filtering.md
violation_hint: "currentPrincipal.tenancyId() or isCrossTenantAdmin() called inside a service, REST endpoint, or business logic class — tenancy checks belong in the data access layer only"
created: 2026-05-20
---

Repository here means the Repository pattern — a data access class that isolates database queries from business logic (not a git repository). Every such class reads tenancyId once from CurrentPrincipal and applies it unconditionally to every query. Call sites (services, endpoints) never touch CurrentPrincipal directly for tenancy purposes — they just invoke the data access class and get back already-scoped results. Cross-tenant data access classes require isCrossTenantAdmin() to be true at CDI injection time, enforced via a Quarkus producer; unauthorised code cannot obtain one. Tenancy logic appears in exactly one place per data access class, never scattered across the codebase.
