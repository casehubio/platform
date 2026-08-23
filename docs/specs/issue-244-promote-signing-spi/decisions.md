## D1: SPI scope — sign + verify as separate concerns

**Choice:** Sign SPI (interface with pluggable backends) + verify utility (static helper, pure JCA)
**Alternatives:**
- Sign only — simpler but forces consumers to reimplement non-trivial algorithm detection (EC curve order → ECDSA variant, trial-order key factory loading, ML-DSA support)
- Sign + verify on one interface — conflates an SPI (needs backend injection) with a stateless utility (no dependencies)
**Rationale:** Signing and verification have different natures. Signing needs a backend (KMS, HSM, local key) and follows the CredentialResolver SPI pattern. Verification needs only a public key + JCA and follows the UUIDv7/LabelPatternMatcher utility pattern. No algorithm enum — the existing algorithm-transparent pattern (detect from key bytes) is superior because callers don't need crypto knowledge and key rotation changes algorithms transparently. No HMAC — symmetric signing is fundamentally different and would change the result record shape.
**Trade-offs:** Two types instead of one interface. Consumers import from two classes. But the conceptual separation is correct.
**Sources:** AgentSigner.java (ledger/runtime), AgentCryptographicVerifier.java (ledger/runtime), CredentialResolver.java (platform-api), SignatureAlgorithms.java (ledger/runtime)
**Exploration:** deep-analysis
**Status:** captured

## D2: SPI method keying and naming — actorId, `SigningProvider`

**Choice:** `sign(String actorId, byte[] data) → Optional<SignatureResult>` with `default keyMaterial(actorId)`. Named `SigningProvider` (agent noun pattern, consistent with CredentialResolver, PreferenceProvider, AccessControlProvider).
**Alternatives:**
- credentialRef keying — follows CredentialResolver pattern but mismodels the domain (signing is identity-based, not credential-based). actorId is a flexible lookup key — "claude:reviewer@v1", "qhorus:service", "org:root" all work.
- Dual-keyed request object — platform SPIs aren't consistently request-object-based (CredentialResolver.resolve(String), DIDResolver.resolve(String) both use direct parameters). Two params doesn't warrant a request object. Extensibility path: add `default sign(SigningRequest)` later if needed.
**Rationale:** Signing is "sign AS this identity." The actorId maps to a key in whatever backend is deployed. The existing `AgentSigner` interface has this shape and it's well-tested across 4 KMS backends.
**Trade-offs:** Less flexible than a request object. Extensibility via default method addition if a future consumer needs algorithm hints or credential routing.
**Sources:** AgentSigner.java (ledger/runtime), CredentialResolver.java (platform-api), platform SPI naming survey (R1-08)
**Exploration:** quick
**Review note (R1-03):** Request object alternative was challenged as under-examined. Rebuttal: actorId is a flexible lookup key (not person-specific), platform SPIs aren't uniformly request-object-based, and default method addition provides an upgrade path without SPI break.
**Status:** captured

## D3: @DefaultBean pattern — loud no-op

**Choice:** `NoOpSigningProvider` returns `Optional.empty()` for all actors AND logs WARN on first invocation per actorId. Signing is optional — unsigned artifacts are a valid state — but misconfiguration should be visible.
**Alternatives:**
- Silent no-op (original choice) — returns empty without logging. Appropriate for non-security-relevant capabilities (NoOpCaseMemoryStore) but signing misconfiguration can mask integrity gaps.
- Configurable mock — returns deterministic test signatures driven by @ConfigProperty. More useful for integration tests but adds complexity; a real test fixture is better.
**Rationale:** Signing misconfiguration is security-relevant (artifacts that should be signed go out unsigned). NoOpAgentProvider already follows the "warn on misconfiguration" pattern. First-invocation-per-actorId guard prevents log flooding.
**Trade-offs:** Tests that need real signatures must add a signing backend or provide their own @Alternative. WARN logs in test output when no signing backend is configured (acceptable — tests should configure what they need).
**Depends on:** D1 (scope determines what the @DefaultBean needs to implement)
**Sources:** NoOpCaseMemoryStore (platform/), NoOpAgentProvider (platform/), CredentialResolver (platform-api), AgentSigner (ledger/runtime), review R1-04
**Exploration:** quick (revised after review R1-04)
**Review note (R1-04):** Revised from silent to loud no-op after reviewer correctly identified signing misconfiguration as security-relevant.
**Status:** revised
