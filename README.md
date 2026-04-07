# BFF Reference Architecture

A runnable reference implementation of the **Backend-for-Frontend (BFF)** pattern
with a token-free Angular SPA, Keycloak as Identity Provider, Redis-backed
sessions and three aggregated downstream microservices.

The goal of this project is to demonstrate enterprise-grade architecture
patterns end-to-end — security, session handling, parallel aggregation,
hexagonal layering, test coverage and accessibility — in a way that can be
launched locally with a single command.

---

## Architecture at a glance

```
            ┌──────────────┐         ┌──────────────┐
  Browser → │   Angular    │  ─────► │   nginx      │
            │  (no tokens) │         │ (static SPA  │
            └──────────────┘         │  + reverse   │
                                     │   proxy)     │
                                     └──────┬───────┘
                                            │ same-origin
                                            ▼
                                     ┌──────────────┐         ┌─────────────┐
                                     │     BFF      │ ◄─────► │   Keycloak  │
                                     │ Spring Boot  │  OIDC   │   (OIDC)    │
                                     │   WebFlux    │         └─────────────┘
                                     │              │
                                     │  ┌────────┐  │         ┌─────────────┐
                                     │  │Session │◄─┼───────► │    Redis    │
                                     │  │ Tokens │  │         └─────────────┘
                                     │  └────────┘  │
                                     │              │
                                     │   parallel   │
                                     │   Mono.zip   │
                                     └──┬───┬───┬───┘
                                        │   │   │
                       ┌────────────────┘   │   └──────────────────┐
                       ▼                    ▼                      ▼
                ┌──────────────┐    ┌──────────────┐        ┌──────────────┐
                │ user-service │    │ notification │        │   activity   │
                │   (REST,     │    │   service    │        │   service    │
                │  JWT-secured)│    │  (REST,      │        │   (REST,     │
                └──────────────┘    │  JWT-secured)│        │  JWT-secured)│
                                    └──────────────┘        └──────────────┘
```

A more detailed view (including sequence diagrams for login, API call and
logout) lives in [`docs/architecture.md`](docs/architecture.md).

---

## Repository layout

```
bff-reference/
├── docker-compose.yml          # one-shot bring-up of the whole stack
├── .env.example                # all configurable values
├── keycloak/                   # realm export + Keycloak Dockerfile
├── bff/                        # Spring Boot 3 BFF (WebFlux)
├── services/
│   ├── user-service/           # Spring Boot 3, Resource Server
│   ├── notification-service/
│   └── activity-service/
├── frontend/                   # Angular 21 SPA, served by nginx
└── docs/
    ├── architecture.md
    ├── security-concept.md
    └── adr/                    # Architecture Decision Records
```

---

## Prerequisites

- Docker and Docker Compose (Compose v2)
- Java 21 LTS  *(only required if you want to run `mvn verify` outside Docker)*
- Maven 3.9+   *(ditto)*
- Node 20+     *(only required for `ng build` outside Docker)*

The Quickstart below needs **only Docker**.

---

## Quickstart

```bash
cp .env.example .env
docker compose up --build
```

Then open <http://localhost> in a browser and log in with the demo user:

| Username             | Password |
|----------------------|----------|
| `demo@example.com`   | `demo123`|

You should land on the dashboard which shows data from all three downstream
services aggregated by the BFF in a single response.

The Keycloak admin UI is reachable at <http://localhost:8080> with the
credentials from `.env` (`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`,
default `admin` / `admin`).

---

## Why a BFF?

The Backend-for-Frontend pattern places a server-side component between the
SPA and the actual business APIs. In this project the BFF owns the entire
OAuth2/OIDC dance with Keycloak, holds the access and refresh tokens in a
server-side session store (Redis), and exposes a small, frontend-shaped HTTP
API that the Angular app consumes via plain session cookies.

The benefits we want to showcase:

- **No tokens in the browser.** The SPA never sees an access token, refresh
  token or id token. There is no `localStorage` / `sessionStorage` /
  in-memory token cache that could be stolen by XSS.
- **Aggregation in one place.** The dashboard view needs data from three
  microservices. Doing the fan-out in the BFF (`Mono.zip`, parallel,
  per-service timeout, partial-failure tolerant) keeps the SPA dumb and the
  network round-trips low.
- **Strong CSRF posture.** Because the SPA authenticates with cookies, we
  combine `SameSite=Lax` with Spring Security's double-submit CSRF token. The
  rationale lives in [`docs/security-concept.md`](docs/security-concept.md).
- **A single security boundary.** Token validation, refresh handling and
  logout invalidation all live in the BFF — the downstream services only
  validate JWTs.

The full rationale is captured as ADRs in [`docs/adr/`](docs/adr/).

---

## Token-free frontend

The Angular frontend deliberately does **not** depend on `angular-oauth2-oidc`
or any other browser-side OIDC client. It only knows three things:

1. Send every API call with `withCredentials: true` so the session cookie is
   attached automatically.
2. If a response comes back with HTTP 401, navigate the browser to `/login`
   and let the BFF start the OIDC Authorization Code Flow.
3. For state-changing requests, send the CSRF token from the `XSRF-TOKEN`
   cookie back as the `X-XSRF-TOKEN` header (Angular's `HttpClient` does this
   transparently when configured with `withXsrfConfiguration`).

That is the entire authentication contract on the client side.

---

## Session and cookie strategy

| Property         | Value                    | Reason                                          |
|------------------|--------------------------|-------------------------------------------------|
| Cookie name      | `SESSION`                | Spring Session default                          |
| `HttpOnly`       | `true`                   | Not accessible to JavaScript (XSS mitigation)   |
| `Secure`         | `true` (`false` in dev)  | Force HTTPS in non-local environments           |
| `SameSite`       | `Lax`                    | Survives top-level navigation from Keycloak     |
| `Path`           | `/`                      | Whole BFF surface                               |
| `Max-Age`        | `BFF_SESSION_TIMEOUT_SECONDS` | Aligned with Keycloak refresh token lifespan |

- The `SESSION` cookie stores only an opaque session id. The actual access
  and refresh tokens live in **Redis**, keyed by that session id.
- A second cookie `XSRF-TOKEN` carries the CSRF token (not `HttpOnly`, by
  design — the SPA must read it to echo it back as a header).
- Logout revokes the refresh token at Keycloak, deletes the Redis entry and
  expires both cookies in the browser.

For the threat model and the alternatives we considered, see
[`docs/security-concept.md`](docs/security-concept.md).

---

## Redis layout

The BFF uses Spring Session (`spring-session-data-redis`) which writes session
state under keys of the form:

```
spring:session:sessions:<session-id>           # session attributes
spring:session:expirations:<bucket>            # expiry index
spring:session:sessions:expires:<session-id>   # per-session expiry marker
```

Inside the session attributes the OAuth2 access and refresh tokens are stored
via Spring's `OAuth2AuthorizedClient` mechanism, never plain text in any other
key.

---

## Keycloak

A complete realm export lives in `keycloak/realm-export.json` and is imported
on container start via `start-dev --import-realm`. It contains:

- Realm `bff-demo`
- Confidential client `bff-client` (Authorization Code Flow, secret from
  `.env`)
- Test user `demo@example.com` / `demo123`
- Brute-force protection enabled
- Access token lifespan: 5 minutes; refresh token lifespan: 30 minutes

The Keycloak admin UI is at <http://localhost:8080> using
`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` from `.env`.

---

## Tests

### Backend (BFF + services)

```bash
cd bff && mvn verify
```

`mvn verify` runs:

- Unit tests (`*Test.java`, surefire)
- Integration tests (`*IT.java`, failsafe, Testcontainers)
- JaCoCo coverage gate: ≥ 80 % line, ≥ 70 % branch — the build fails on
  underrun.

The other three services follow the same layout. Run `mvn verify` inside any
of them or from the project root with a multi-module wrapper if you add one.

### Frontend

```bash
cd frontend
npm ci
npx ng build --configuration production
```

For interactive component tests, `npm test` runs the Karma/Jest harness
configured in `angular.json`.

---

## Accessibility (BITV 2.0 / WCAG 2.1 AA)

Accessibility is treated as a hard requirement, not a nice-to-have:

- Native semantic HTML (`header`, `main`, `nav`, `article`, `dl`, `button`)
  is preferred over ARIA wrappers.
- Every interactive element is reachable and operable by keyboard, with a
  highly visible `:focus-visible` outline.
- Loading and error states are exposed via `role="status"` /
  `aria-live="polite"` so screen readers announce them.
- Color palette and focus colors meet WCAG 2.1 AA contrast ratios in both
  light and dark mode (`prefers-color-scheme`).
- A skip-link allows keyboard users to bypass the header.
- `prefers-reduced-motion` is respected.
- No accessibility overlays — accessibility is implemented at the markup
  level.

---

## Architecture decisions

| ADR | Topic |
|-----|-------|
| [ADR-001](docs/adr/ADR-001-bff-pattern.md) | Why BFF over direct SPA → API |
| [ADR-002](docs/adr/ADR-002-session-cookie.md) | Why session cookie over browser-held tokens |
| [ADR-003](docs/adr/ADR-003-redis-session-store.md) | Why Redis as session store |
| [ADR-004](docs/adr/ADR-004-webflux-aggregation.md) | Why WebFlux for parallel aggregation |
| [ADR-005](docs/adr/ADR-005-maven-build.md) | Why Maven over Gradle |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `docker compose up` hangs on `keycloak` healthcheck | First-time realm import takes ~30 s | Wait for `start_period`, then check `docker compose logs keycloak` |
| Login redirects loop between `/login` and Keycloak | `KEYCLOAK_PUBLIC_ISSUER_URI` does not resolve from your browser | Make sure it points to `http://localhost:8080/...` and that port 8080 is published |
| `403` on POST/PUT/DELETE | Missing or stale CSRF cookie | Reload the SPA — Angular re-reads `XSRF-TOKEN` and echoes it as header |
| Dashboard widgets show "no data" | One downstream service is down | The BFF deliberately returns a partial response; check `docker compose ps` |
| `mvn verify` fails on JaCoCo gate | Coverage dropped below threshold | Add tests; the threshold is 80 % line / 70 % branch by design |

---

## License

This is a reference / educational project. Use at your own risk.
