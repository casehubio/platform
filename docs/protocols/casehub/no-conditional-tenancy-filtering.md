---
id: PP-20260520-439daf
title: "Never gate tenancy filtering on a deployment mode or feature flag"
type: rule
scope: platform
applies_to: "all modules — any code that queries data, publishes events, constructs cache keys, or writes audit entries"
severity: critical
violation_hint: "if (multiTenantEnabled) { filterByTenancyId() } — any conditional that skips tenancyId filtering based on configuration or environment"
created: 2026-05-20
---

tenancyId filtering must always execute — unconditionally, in every query, event, cache key, and audit entry. The deployment model (single-tenant vs multi-tenant) determines what value tenancyId carries, not whether the filtering code runs. In a single-tenant deployment, CurrentPrincipal returns a fixed sentinel (e.g. "default"); every filter is always satisfied and the overhead is negligible. Conditional filtering produces two code paths — one of which has no tenancy isolation — and is a data leakage vulnerability waiting to be enabled.
