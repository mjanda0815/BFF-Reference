# Architecture

This document describes the architecture of the BFF reference project: the
components, how they communicate, and the three core data flows (login, API
call and logout).

## Component overview

```mermaid
flowchart LR
    Browser([Browser])
    subgraph Edge["Edge / nginx"]
        SPA[Angular SPA<br/>static bundle]
        Proxy[nginx reverse proxy]
    end
    subgraph BFFNode["BFF (Spring Boot WebFlux)"]
        Sec[SecurityFilterChain<br/>OAuth2 client + CSRF]
        Agg[DashboardAggregationService]
        Tok[SessionTokenService]
        Clients[WebClients<br/>per downstream service]
    end
    KC[(Keycloak<br/>OIDC IdP)]
    R[(Redis<br/>Spring Session)]
    US[user-service]
    NS[notification-service]
    AS[activity-service]

    Browser -- HTTP / cookies --> Proxy
    Proxy -- static --> SPA
    Proxy -- /api, /login, /logout, /oauth2 --> Sec
    Sec --> Agg
    Sec --> Tok
    Tok <--> R
    Sec <-- OIDC --> KC
    Agg --> Clients
    Clients -- JWT bearer --> US
    Clients -- JWT bearer --> NS
    Clients -- JWT bearer --> AS
    US -- JWKS --> KC
    NS -- JWKS --> KC
    AS -- JWKS --> KC
```

### Components

| Component             | Tech                          | Responsibility                                                                 |
|-----------------------|-------------------------------|--------------------------------------------------------------------------------|
| Angular SPA           | Angular 21, standalone, signals | Render dashboard, no auth logic, no token handling                           |
| nginx                 | nginx alpine                  | Serve SPA, reverse-proxy `/api`, `/login`, `/logout`, `/oauth2` to BFF         |
| BFF                   | Spring Boot 3 WebFlux         | OAuth2 client, session, CSRF, parallel aggregation                             |
| Keycloak              | Keycloak 26                   | OIDC Identity Provider, JWKS endpoint                                          |
| Redis                 | Redis 7 alpine                | Session store (`spring-session-data-redis`)                                    |
| user-service          | Spring Boot 3 Resource Server | Profile data; validates JWT against Keycloak JWKS                              |
| notification-service  | Spring Boot 3 Resource Server | Notifications; same JWT validation                                             |
| activity-service      | Spring Boot 3 Resource Server | Activity events; same JWT validation                                           |

### Hexagonal layout (BFF)

```
io.janda.bff
├── BffApplication
├── config        # SecurityConfig, RedisConfig, WebClientConfig, CorsConfig, BffProperties
├── domain
│   ├── model     # DashboardData, UserProfile, Notification, ActivityEvent…
│   └── port      # UserServicePort, NotificationServicePort, ActivityServicePort
├── application   # DashboardAggregationService, SessionTokenService
├── adapter
│   ├── web       # DashboardController, AuthController + DTOs
│   └── client    # UserServiceClient, NotificationServiceClient, ActivityServiceClient
└── security      # SessionInvalidationHandler, custom CSRF, BFF-specific filters
```

The application layer talks to ports (interfaces in `domain.port`); the
client adapters in `adapter.client` are the only place where `WebClient`
appears, and they implement those ports. The web adapters in `adapter.web`
talk to the application services. This makes the inner core fully testable
without Spring or HTTP.

---

## Data flows

### 1. Login flow

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant B as Browser
    participant N as nginx
    participant BFF as BFF (Spring Security)
    participant KC as Keycloak
    participant R as Redis

    U->>B: opens http://localhost
    B->>N: GET /
    N-->>B: index.html (Angular bundle)
    B->>N: GET /api/userinfo (auth.guard probe)
    N->>BFF: GET /api/userinfo
    BFF-->>N: 401 Unauthorized
    N-->>B: 401
    B->>N: GET /login (browser navigation)
    N->>BFF: GET /login
    BFF-->>B: 302 to Keycloak /authorize
    B->>KC: GET /authorize
    U->>KC: enters credentials
    KC-->>B: 302 to BFF /login/oauth2/code/keycloak?code=...
    B->>BFF: GET /login/oauth2/code/keycloak?code=...
    BFF->>KC: POST /token (exchange code)
    KC-->>BFF: access_token + refresh_token + id_token
    BFF->>R: store OAuth2AuthorizedClient under session id
    BFF-->>B: 302 to / + Set-Cookie SESSION + XSRF-TOKEN
    B->>N: GET /
    N-->>B: SPA, now authenticated
```

### 2. API call flow (dashboard aggregation)

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser (SPA)
    participant N as nginx
    participant BFF as BFF
    participant R as Redis
    participant US as user-service
    participant NS as notification-service
    participant AS as activity-service

    B->>N: GET /api/dashboard (Cookie: SESSION=...)
    N->>BFF: GET /api/dashboard
    BFF->>R: load session + OAuth2AuthorizedClient
    R-->>BFF: access_token (refresh if expired)
    par Parallel fan-out
        BFF->>US: GET /users/{id} (Bearer)
        BFF->>NS: GET /notifications/{id} (Bearer)
        BFF->>AS: GET /activity/{id} (Bearer)
    end
    US-->>BFF: profile JSON
    NS-->>BFF: notifications JSON
    AS-->>BFF: activity JSON
    BFF->>BFF: Mono.zip(...) → DashboardData
    BFF-->>N: 200 DashboardData
    N-->>B: 200 DashboardData
```

The aggregation uses `Mono.zip` so all three downstream calls run
**concurrently** on the WebFlux event loop. Each call has a 5-second
timeout. If a single service fails or times out, the BFF returns a partial
response (the failing widget gets a neutral default) instead of failing the
whole dashboard. This is what *resilient aggregation* means in this project.

### 3. Logout flow

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser (SPA)
    participant N as nginx
    participant BFF as BFF
    participant R as Redis
    participant KC as Keycloak

    B->>N: POST /logout (X-XSRF-TOKEN header + cookie)
    N->>BFF: POST /logout
    BFF->>BFF: validate CSRF (double-submit)
    BFF->>KC: POST /revoke refresh_token
    BFF->>R: delete session + AuthorizedClient
    BFF-->>B: 204 + Set-Cookie SESSION=; Max-Age=0
    B->>B: window.location = '/'
    B->>N: GET /
    N-->>B: SPA → auth.guard → 401 → /login
```

The Keycloak token revocation is best-effort: if Keycloak is unreachable,
the local session is still destroyed and the user is logged out from this
BFF. The next login round-trip will then re-authenticate.

---

## Configuration surface

All runtime configuration is read from environment variables (see
`.env.example`). The most important ones:

| Variable                          | Purpose                                                            |
|-----------------------------------|--------------------------------------------------------------------|
| `KEYCLOAK_ISSUER_URI`             | Issuer URL the BFF and services use to talk to Keycloak (internal) |
| `KEYCLOAK_PUBLIC_ISSUER_URI`      | Issuer URL the **browser** is redirected to                        |
| `KEYCLOAK_CLIENT_ID/SECRET`       | OIDC confidential client credentials                               |
| `REDIS_HOST` / `REDIS_PORT`       | Spring Session backing store                                       |
| `BFF_FRONTEND_ORIGIN`             | Allowed CORS origin                                                |
| `BFF_SESSION_TIMEOUT_SECONDS`     | Session cookie max-age, aligned with Keycloak refresh lifespan     |
| `BFF_COOKIE_SECURE`               | `true` in non-local environments                                   |
| `USER/NOTIFICATION/ACTIVITY_SERVICE_URL` | Internal Docker service URLs                                |

---

## Why these choices

For the rationale and the rejected alternatives behind each major decision,
see the ADRs in [`adr/`](adr/) and the threat model in
[`security-concept.md`](security-concept.md).
