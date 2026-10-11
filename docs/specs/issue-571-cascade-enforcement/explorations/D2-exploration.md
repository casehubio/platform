# D2 Exploration: Orphan Representation

## Research Summary

### Industry Systems — How They Model Health vs Topology

| System | Health Model | Topology/Orphan Model | Separated? |
|--------|-------------|----------------------|------------|
| Kubernetes | Liveness/Readiness/Startup probes (per-pod) | OwnerReferences on the object + GC DAG | **Yes** — completely independent dimensions |
| Consul | passing/warning/critical (3-state) | Catalog membership + synthetic-node reconciliation | **Yes** — orphans are a catalog problem, not a health state |
| ZooKeeper | Session-based (alive/expired) | Znode tree structure (ephemeral nodes can't have children) | **Yes** — by structural constraint |
| Eureka | Heartbeat lease renewal | N/A (flat registry, no parent-child) | N/A |

**Key finding: no mature registry system collapses health and topology into a single field.**

### Kubernetes Deep Dive — The Reference Model

Kubernetes is the most relevant comparison because it handles exactly this problem:
- **Owner references** stored on the dependent object establish parent-child relationships
- **Propagation policy** (Background/Foreground/Orphan) is specified at deletion time
- When deleted with `--cascade=orphan`, the owner reference is simply *removed* from dependents — there is no "orphaned" flag on the pod. The pod continues running with its liveness probe status unchanged.
- The GC builds an internal DAG and detects orphans by checking "does this object have any owner references?"

Critical insight: in Kubernetes, "orphaned" is a **derived state** (no owner references) rather than a **stored state** (an explicit flag).

### Consul Orphan Problem

Consul doesn't model orphans in health status. Orphaned registrations (sidecars whose parent service was deregistered) are a bug — they show as "passing" health-wise but their parent is gone. Consul uses external reconciliation (synthetic-node comparison) to detect and clean them up. The health model (passing/warning/critical) remains strictly about liveness.

### Multi-Dimensional Health (Industry Best Practice)

Research shows consensus around 3-4 health dimensions that should NOT be collapsed:
- **Liveness:** Is the process alive? (heartbeat/TTL)
- **Readiness:** Can it serve requests? (capability check)
- **Capability:** Can it perform specific business functions? (domain-specific)
- **Topology:** Are its dependencies/dependents intact? (relationship graph)

## First-Principles Analysis

### State Dimensions

A registry entry has genuinely orthogonal state dimensions:

| Dimension | Values | Source |
|-----------|--------|--------|
| Liveness | HEALTHY, DEGRADED, DOWN | Heartbeat/TTL |
| Topology | attached, orphaned | Cascade on parent deregistration |
| Registration | present, absent | register/deregister |

An entry can be HEALTHY + orphaned (heartbeating fine, but parent gone) or DOWN + attached (stopped heartbeating, parent still there). These combinations are meaningful and should be representable.

### Approaches

#### A: HealthStatus.ORPHANED
- Simple enum addition
- **Information loss**: can't be HEALTHY and ORPHANED simultaneously
- Against every mature registry system's design
- Breaks existing HealthStatus consumers that switch on the enum

#### B: Boolean `orphaned` field on RegistryEntry
- Clean separation from health
- All state combinations representable
- Simple to query
- One field addition to a pre-release record

#### C: Metadata flag ("orphaned"="true")
- No SPI change
- Opaque, not queryable via discover(), requires convention
- Weakest option

#### D: `Instant orphanedAt` field (nullable)
- Single field gives both boolean status AND timestamp
- Matches existing `lastHeartbeat` pattern (nullable Instant)
- Enables time-based cleanup: "deregister if orphanedAt + threshold < now" (parallel to Consul's `deregister_critical_service_after`)
- `withOrphaned(Instant)` wither mirrors `withHealth()` and `withHeartbeat()`
- `isOrphaned()` convenience method

#### E: Derived state (no field — orphan detected from relationship graph)
- Follows the Kubernetes model most closely
- "Orphaned" = "has no inbound relationships of ownership type"
- No new fields on RegistryEntry
- Problem: can't distinguish "never had a parent" from "had a parent that was removed"
- Problem: requires a join against relationships for every orphan query

#### F: `Set<EntryCondition>` (extensible conditions)
- Future-proof for DRAINING, MIGRATING, etc.
- Over-engineered for a single condition (YAGNI)

#### G: `TopologyStatus` enum (ATTACHED, ORPHANED)
- Most principled two-dimensional model
- YAGNI unless more topology states are expected

## Recommendation: D — `Instant orphanedAt`

One nullable field achieves everything:
- Orthogonal to HealthStatus (no information loss)
- Queryable (non-null = orphaned)
- Time-aware (enables cleanup policies)
- Follows existing record patterns (mirrors `lastHeartbeat`)
- Minimal change to a pre-release record
- `withOrphaned(Instant now)` wither completes the wither family

## Sources

- [Kubernetes Owners and Dependents](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/)
- [Kubernetes Garbage Collection](https://kubernetes.io/docs/concepts/architecture/garbage-collection/)
- [Kubernetes Cascading Deletion](https://kubernetes.io/docs/tasks/administer-cluster/use-cascading-deletion/)
- [Consul Health Checks](https://developer.hashicorp.com/consul/docs/register/health-check/vm)
- [Consul Orphan Sidecar Issue](https://github.com/hashicorp/consul/issues/15908)
- [Health Checks in Distributed Systems: Beyond Liveness](https://geekworkbench.com/blog/technical/health-checks-distributed-systems/)
- [ZooKeeper Programmer's Guide](https://zookeeper.apache.org/doc/current/zookeeperProgrammers.html)
