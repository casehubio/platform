## D1: Retrofit scope — full API surface vs SPI methods only

**Choice:** Full retrofit — `ResourceId` replaces `String resourceId` across the entire ACL API surface: `AccessControlProvider` methods, `AclEntryRequest`, `AclEntry`, `AclPage`, `AccessDeniedException`, `AclEntryInput`, and `ParentInput`. `AclQuery.resourceType` stays as `String` (it's a type prefix, not a resource identifier).
**Alternatives:**
- SPI methods only — change `AccessControlProvider` signatures but leave records as-is; callers convert at the boundary. Creates a mixed API where some types are structured and others aren't.
**Rationale:** Consistency across the ACL API. A mixed API where some types use `ResourceId` and others use `String` is confusing and forces conversion boilerplate at every boundary. Full retrofit means one representation throughout.
**Trade-offs:** Larger surface area of change. Every consumer that constructs `AclEntry`, `AclEntryRequest`, or catches `AccessDeniedException` must update. Acceptable — pre-release platform.
**Exploration:** quick
**Status:** captured

## D2: DB storage format — keep type:id strings vs split columns

**Choice:** Keep `type:id` string format in DB. `ResourceId.toString()` on write, `ResourceId.parse()` on read. No Flyway migration.
**Alternatives:**
- Split into two columns (`resource_type`, `resource_id`) — cleaner schema, native SQL filtering by type without LIKE, but requires a Flyway migration and rewriting all JPA queries.
**Rationale:** The DB already stores in `type:id` format. All existing LIKE queries and wildcard resolution work unchanged. `ResourceId.parse()/toString()` handles the conversion cleanly at the JPA boundary. No migration = less risk, less work, same functionality.
**Trade-offs:** LIKE-based type filtering is slightly less efficient than column equality, but has been working fine. If query performance on type filtering becomes an issue, split can be done as a follow-up.
**Exploration:** quick
**Status:** captured
