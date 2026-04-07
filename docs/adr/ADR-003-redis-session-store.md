# ADR-003: Use Redis as the session store

- **Status:** Accepted
- **Date:** 2026-04-06

## Context

The BFF holds per-user state: the Spring Security context, the
`OAuth2AuthorizedClient` (access token + refresh token + expiry) and any
CSRF state. This state has to survive:

- the BFF process being restarted or redeployed,
- the BFF running as **more than one instance** behind a load balancer,
- a few minutes of client inactivity without forcing a re-login.

Possible backing stores:

1. **In-memory** (default servlet `HttpSession`). Lost on restart, not
   shared between instances.
2. **JDBC** via `spring-session-jdbc`. Requires a database and pushes
   session traffic through SQL, which is heavier than strictly necessary.
3. **Redis** via `spring-session-data-redis`.
4. **Hazelcast / Infinispan**. Adds a full clustering stack the project
   does not otherwise need.

## Decision

We use **Redis 7** via `spring-session-data-redis` with the Lettuce driver.

## Consequences

### Positive

- **Restart-safe and horizontally scalable.** Multiple BFF instances can
  share the same Redis and see the same sessions.
- **TTL for free.** Redis keys carry a native TTL; Spring Session wires the
  session timeout onto that TTL so expired sessions clean themselves up.
- **Low operational overhead.** A single `redis:7-alpine` container with
  no persistence (`--save "" --appendonly no`) is sufficient for a session
  store — sessions are by definition ephemeral, losing them on Redis
  failure is acceptable (users simply log in again).
- **First-class Spring support.** `@EnableRedisHttpSession`, Lettuce
  connection factory, serializers and health indicators are all provided
  out of the box.

### Negative

- **Redis is now a runtime dependency.** The BFF cannot start without it.
  This is acknowledged in the compose file via a healthcheck and
  `depends_on.condition: service_healthy`.
- **Serialization.** Spring Session serializes session attributes with
  JDK serialization by default, which means adding fields to session
  classes has to be backwards-compatible or paired with a cache flush.

### Rejected alternative: JDBC session store

- Would require a database just for sessions.
- Session writes on every request turn into SQL traffic, which is much
  more expensive per request than a Redis `SET`.
- Adds schema migrations to a component whose entire data model is "a
  session id points to a blob".

### Rejected alternative: sticky sessions + in-memory

- Ties users to a specific BFF instance. Rolling deploys log everyone out.
- Does not survive instance crashes.
- Fights against cloud-native rolling update patterns.
