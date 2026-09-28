# Decisions — parent#483 Spring REST controllers for hand-written @Path resources

## D1: Scope reduction — 3 of 6 resources already generated

**Choice:** Only 3 resources need work; 3 are already covered by existing @McpDomain generators
**Alternatives:**
- Implement all 6 as originally described — unnecessary; PreferenceSchemaResource, SubscriptionService, EventTypeService already have generated REST via @McpDomain
**Rationale:** Code examination confirmed PreferenceSchemaResource was migrated to @McpDomain("preference-schemas"), and subscriptions uses @McpDomain with @PlatformQuery/@PlatformMutation. No work needed.
**Trade-offs:** None — pure scope clarification
**Sources:** callback-client CallbackDispatchResource.java, notification-dispatch EngagementCallbackResource.java, streams-webhook WebhookResource.java, preferences-editor PreferenceSchemaService.java, subscriptions-core SubscriptionService.java
**Exploration:** quick
**Status:** captured

## D2: Uniform generator approach for all 3 resources

**Choice:** Extend graphql-spring-generator to support @PlatformWebhook, migrate WebhookResource to @McpDomain
**Alternatives:**
- Hand-write 3 Spring controllers — simpler upfront but creates maintenance burden and breaks generator-first convention
- Hybrid (generator for @McpDomain resources, rest-spring-generator for WebhookResource) — adds a second generator dependency for one resource
**Rationale:** The APT already handles @PlatformWebhook — the logic exists, it just needs porting to the shared scanner. Generator-first is the established convention. Webhook endpoints will recur in consumer repos, so the investment pays off beyond this issue.
**Trade-offs:** More upfront work (~M) to extend the shared scanner and Spring writer. But eliminates all future hand-writing for webhook-type endpoints.
**Sources:** graphql-spring-generator SpringDomainRestControllerWriter.java, generator-common McpDomainJandexScanner.java, graphql-generator GraphQLAnnotationProcessor.java (APT internal scanner with WEBHOOK support)
**Exploration:** quick
**Status:** captured

## D3: WebhookResource migration to @McpDomain

**Choice:** Migrate streams-webhook WebhookResource from raw @Path to @McpDomain with @PlatformWebhook on the SPI interface in streams-webhook-core
**Alternatives:**
- Keep raw @Path and use rest-spring-generator — adds second generator dependency for one resource, inconsistent pattern
**Rationale:** WebhookResource already has a -core POJO (WebhookReceiver). Adding @McpDomain to the SPI makes it uniform with callback-client and notification-dispatch patterns. The Quarkus graphql-generator APT will generate the JAX-RS resource, and graphql-spring-generator will generate the Spring controller.
**Trade-offs:** Touches the Quarkus side (new @McpDomain annotation on SPI), but the change is additive — existing WebhookResource is replaced by generated equivalent.
**Sources:** streams-webhook WebhookResource.java, streams-webhook-core WebhookReceiver.java
**Exploration:** quick
**Status:** captured

## D4: Generated webhook controller placement

**Choice:** streams-webhook Spring controller goes in streams-spring (existing module)
**Alternatives:**
- New streams-webhook-spring module — more granular but adds another module when streams-spring already consolidates all stream adapters
**Rationale:** streams-spring already handles Kafka, AMQP, Camel, and Poll adapters. Webhook is another stream ingestion mechanism — same concern, same module.
**Trade-offs:** streams-spring gets slightly larger, but it's already the consolidation point for stream adapters.
**Sources:** streams-spring/ module (hand-written Kafka/AMQP/Camel/Poll auto-configs)
**Exploration:** quick
**Depends on:** D3 (WebhookResource must be @McpDomain before generator can produce Spring controller)
**Status:** superseded by D5

## D5: Pivot — fix existing rest-spring-generator output instead of migrating to graphql-spring-generator

**Choice:** Fix rest-spring-generator's status code handling for DispatchResult and WebhookResult
**Alternatives:**
- Proceed with graphql-spring-generator migration (D2-D4) — more principled but much more work for same functional outcome
- Close as done — controllers exist but status code mapping is broken
**Rationale:** Late-stage discovery that platform-spring already generates Spring REST controllers for all 3 resources via rest-spring-generator (pom.xml lines 216-251). CallbackDispatchController and WebhookController ignore their return type's status() — always returning 204. The fix is to teach rest-spring-generator about status-bearing return types, not to migrate to a different generator.
**Trade-offs:** Does not add @PlatformWebhook to the shared scanner — that capability remains APT-only. But the immediate goal (working Spring controllers) is met with much less risk and scope.
**Sources:** platform-spring/pom.xml (rest-spring-generator config), generated CallbackDispatchController, generated WebhookController
**Exploration:** quick
**Depends on:** D1 (scope reduced to 3 resources)
**Supersedes:** D2, D3, D4
**Status:** captured
