# ADR-004: Use Spring WebFlux for the BFF aggregation layer

- **Status:** Accepted
- **Date:** 2026-04-06

## Context

The BFF's main value-add is aggregating three downstream services in one
request. For the `GET /api/dashboard` endpoint we want:

- All three downstream calls to run **in parallel**, not sequentially.
- A per-service **timeout** so a slow service cannot block the whole
  dashboard.
- **Partial-failure tolerance**: if one service is down, return the other
  two widgets plus a neutral placeholder instead of failing the request.
- **Backpressure-friendly IO**, because most of what the BFF does is sit
  on network sockets waiting for downstream responses.

The two realistic choices on Spring Boot 3 are:

1. **Spring MVC + `RestTemplate`/`RestClient`** with a thread pool that
   fans out the calls via `CompletableFuture.supplyAsync`.
2. **Spring WebFlux + `WebClient`** with reactive composition
   (`Mono.zip`, `timeout`, `onErrorResume`).

## Decision

We use **Spring WebFlux** with `WebClient` for the BFF. The downstream
microservices, in contrast, remain on the traditional servlet stack
(`spring-boot-starter-web`) because their job is trivial and latency there
is not the point.

## Consequences

### Positive

- **Idiomatic parallel composition.** `Mono.zip(a, b, c)` runs the three
  downstream calls concurrently without any manual thread-pool or future
  bookkeeping. The resulting code reads like a specification of what the
  endpoint does.
- **Per-call `.timeout(Duration)` and `.onErrorResume(...)`.** Exactly the
  operators we need for resilient aggregation — timeouts and partial
  failure fall out of the reactor API rather than being bolted on.
- **Few threads under load.** The BFF is IO-bound. WebFlux's event-loop
  model needs a small fixed number of threads regardless of the number of
  concurrent aggregations.
- **First-class support for OAuth2 client.** Spring Security's reactive
  `ServerOAuth2AuthorizedClientManager` integrates cleanly with `WebClient`
  so access-token refresh is transparent to the aggregation service.

### Negative

- **Reactive is a different programming model.** Stack traces, debugging
  and blocking-by-accident are harder than with imperative code. The
  aggregation service deliberately keeps the reactive surface small: the
  application layer exposes `Mono<DashboardData>`, and nothing deeper in
  the code blocks.
- **Mixing with blocking libraries is dangerous.** We avoid it; everything
  that talks to the outside world (Keycloak, downstream services, Redis
  via Lettuce) is already non-blocking.
- **Test authoring** uses `StepVerifier` / `WebTestClient` instead of the
  more familiar `MockMvc`. Not a cost so much as a learning curve.

### Why the microservices stay on Spring MVC

Each microservice does a single database-free lookup. There is no
aggregation and no meaningful IO to parallelize. Spring MVC is simpler for
that job and keeps the servlet-based Resource Server configuration short.
