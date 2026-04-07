# ADR-001: Use a Backend-for-Frontend (BFF) instead of direct SPA → API calls

- **Status:** Accepted
- **Date:** 2026-04-06

## Context

The Angular dashboard needs data from three downstream microservices
(user, notification, activity) that are secured by OAuth2 / OIDC via
Keycloak. Two shapes of architecture were considered:

1. **Direct**: the SPA holds its own OAuth2 client, obtains an access token
   from Keycloak and calls each microservice directly, aggregating the
   responses in the browser.
2. **BFF**: a server-side component owns the OAuth2 flow, talks to the
   microservices on behalf of the user, aggregates, and exposes a single
   frontend-shaped API to the SPA over session cookies.

## Decision

We use a **BFF**. The Angular application only talks to the BFF, never
directly to Keycloak or to any downstream service. Tokens never reach the
browser.

## Consequences

### Positive

- **No browser-held tokens.** Access and refresh tokens exist only on the
  server side in Redis. XSS cannot steal what is not there. `localStorage`
  is not used for auth at all.
- **Smaller blast radius on XSS.** A successful XSS gives the attacker the
  authority of the current page (they can still make calls as the user via
  the session cookie), but they cannot exfiltrate long-lived credentials
  that work outside the browser.
- **Frontend stays dumb.** The SPA does not implement OIDC flows, token
  refresh, token storage or JWT parsing. It is a thin presentation layer.
- **Aggregation in one place.** Dashboard data from three services is
  assembled once on the server (`Mono.zip`, parallel, per-service timeout,
  partial-failure tolerant) and delivered in a single response.
- **Stronger CORS posture.** The browser only needs to talk to its own
  origin. Downstream services can stay behind the internal network with no
  CORS concerns at all.

### Negative

- **One more process to run and operate.** The BFF is a new deployable with
  its own scaling, logging and failure modes.
- **Session state.** We now depend on Redis for session storage, which adds
  an operational dependency (see ADR-003 for why Redis specifically).
- **Coupling to frontend shape.** The BFF API is tailored to the current
  SPA. If a second frontend appears, either it consumes the same BFF API or
  a second BFF is needed. This is a deliberate property of the pattern, not
  a bug.

### Rejected alternative: SPA with `angular-oauth2-oidc`

Would have given the SPA its own tokens, which we explicitly do not want
for the security reasons above. It also pushes aggregation into the client,
which adds round-trips and duplicates logic per frontend.
