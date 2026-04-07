# Security Concept

This document describes the threat model of the BFF reference project, the
countermeasures we implemented and — equally important — what is deliberately
out of scope.

## Trust boundaries

```
┌────────────────────┐  untrusted
│      Browser       │  (attacker-controlled JavaScript possible)
└──────────┬─────────┘
           │  HTTP(S), cookies only
           ▼
┌────────────────────┐  trusted
│   nginx + BFF      │
│ (same origin from  │
│  the browser's     │
│  point of view)    │
└──────────┬─────────┘
           │  internal Docker network
           ▼
┌────────────────────┐  trusted
│  Keycloak, Redis,  │
│  downstream svcs   │
└────────────────────┘
```

The only thing the browser ever sees from our system is the nginx origin
(`http://localhost` in dev). It never talks to Keycloak or the microservices
directly.

## Threat model

We focus on the OWASP Top 10 threats that are realistic for a token-free SPA
+ BFF architecture.

| # | Threat                                           | Attack                                                                                                            | Mitigation                                                                                                                                                     |
|---|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Token exfiltration via XSS**                    | Malicious JS reads access/refresh tokens from `localStorage`/memory and posts them to an attacker                 | No tokens in the browser at all. Access/refresh tokens only exist server-side in Redis; the browser holds only an opaque session id in an `HttpOnly` cookie. |
| 2 | **Session hijacking via XSS**                     | Malicious JS reads `document.cookie` and steals the session cookie                                                | `SESSION` cookie is `HttpOnly` → unreachable from JS. Content is an opaque id, useless outside Redis.                                                          |
| 3 | **CSRF on state-changing endpoints**              | Third-party site submits a form to `/logout`, `/api/...` while the user has a valid session                      | Spring Security CSRF with double-submit cookie (`XSRF-TOKEN` + `X-XSRF-TOKEN` header). State-changing methods require both to match.                           |
| 4 | **Session fixation**                              | Attacker obtains a session id, tricks user into using it, then reuses it after authentication                    | Spring Session creates a *new* session id on successful authentication (default `changeSessionId` strategy).                                                   |
| 5 | **Authorization Code interception**               | Attacker on the network intercepts the OAuth2 `code` in flight                                                    | Confidential client (client secret on the BFF), short-lived codes, HTTPS in any non-local environment (`BFF_COOKIE_SECURE=true`).                             |
| 6 | **Open redirect after login**                     | Attacker crafts a login URL with a `redirect_uri` pointing to an external site                                    | Valid redirect URIs are whitelisted in Keycloak (`http://localhost:8090/*`); the BFF only redirects to its own frontend origin after login.                   |
| 7 | **Brute-force login**                             | Attacker tries many passwords against Keycloak                                                                    | Keycloak brute-force protection enabled in the realm export.                                                                                                   |
| 8 | **Sensitive data in logs**                        | Tokens, session ids or PII leak into stdout / logfiles                                                            | No `DEBUG` logs for `org.springframework.security.oauth2`, no `DEBUG` for `org.springframework.web.reactive.function.client`. SLF4J + Logback, structured.    |
| 9 | **Downstream service impersonation**              | Attacker reaches a microservice directly and bypasses the BFF                                                     | Services are only exposed on the internal Docker network; browsers cannot reach them. Services additionally validate JWTs via JWKS against Keycloak.         |
| 10| **Cache poisoning / stale index.html**            | A cached `index.html` references a hashed bundle that no longer exists, or vice versa                            | nginx sets `Cache-Control: no-store` on `index.html` and `immutable` on hashed assets.                                                                         |
| 11| **Clickjacking**                                  | Attacker embeds the SPA in an iframe to trick the user into clicking                                              | `X-Frame-Options: DENY` set in nginx.                                                                                                                          |
| 12| **MIME sniffing**                                 | Browser guesses content type and executes unexpected content                                                      | `X-Content-Type-Options: nosniff` set in nginx.                                                                                                                |

---

## Cookie strategy

Two cookies are used. Both are first-party from the browser's point of view
because nginx reverse-proxies the BFF under the same origin.

### `SESSION` cookie (Spring Session)

| Attribute   | Value                              |
|-------------|-------------------------------------|
| `HttpOnly`  | `true` — not reachable from JS      |
| `Secure`    | `true` in non-local (`BFF_COOKIE_SECURE`) |
| `SameSite`  | `Lax`                              |
| `Path`      | `/`                                |
| `Max-Age`   | `BFF_SESSION_TIMEOUT_SECONDS` (default 1800 s) |
| Contents    | Opaque Spring Session id            |

### `XSRF-TOKEN` cookie (CSRF double-submit)

| Attribute   | Value                              |
|-------------|-------------------------------------|
| `HttpOnly`  | **`false`** — by design, the SPA reads it |
| `Secure`    | `true` in non-local                 |
| `SameSite`  | `Lax`                              |
| `Path`      | `/`                                |
| Contents    | Random CSRF token                   |

### Why `SameSite=Lax` and not `SameSite=Strict`?

`Strict` would block the cookie on any cross-site navigation — including the
top-level redirect from Keycloak back to `http://localhost/login/oauth2/code/keycloak`
after a successful login. The browser would not attach the session cookie
on that redirect, and Spring Security would create a *second* session,
losing the OAuth2 state. Users would end up in a loop.

`Lax` attaches the cookie on top-level GET navigations, which is exactly
what the post-login redirect is, while still blocking it on embedded
cross-site sub-resource requests (the classic CSRF vector). Combined with
the explicit double-submit CSRF token on state-changing endpoints, this
gives us:

| Scenario                                              | `Lax`                          |
|-------------------------------------------------------|--------------------------------|
| Login redirect Keycloak → BFF (top-level GET)         | Cookie sent ✓ (works)           |
| Evil form POST from `attacker.example` → BFF          | Cookie sent, but CSRF check fails → 403 ✓ |
| Hidden `<img>` from `attacker.example` → BFF          | Cookie **not** sent ✓           |
| SPA calling `/api/dashboard` from same origin         | Cookie sent ✓                   |

So `Lax` is the weakest form that still allows the login flow to work, and
the CSRF token closes the gap on state-changing requests.

### Cookie attributes in local dev

In local development `BFF_COOKIE_SECURE=false` because `localhost` is served
over plain HTTP. In any real environment this must flip to `true`. The flag
is read from the environment; there is no hard-coded override.

---

## CSRF strategy: double-submit cookie

Spring Security's `CookieCsrfTokenRepository` (with
`withHttpOnlyFalse()`) is used together with a SPA-aware request handler
(`SpaCsrfTokenRequestHandler`). The contract is:

1. On the first safe request, Spring Security issues an `XSRF-TOKEN` cookie
   (not `HttpOnly`).
2. Angular's `HttpClient` is configured with
   `withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })`
   — it automatically reads the cookie and echoes it back as a header on
   every state-changing request.
3. Spring Security compares the cookie and the header. If they mismatch,
   the request is rejected with 403.

A cross-origin attacker cannot read the `XSRF-TOKEN` cookie (same-origin
policy), therefore cannot forge the header, therefore cannot mount a CSRF
attack — even though the browser might still attach the `SESSION` cookie
under `Lax` for some top-level navigations.

---

## Token lifecycle

- **Access token**: 5 minutes (Keycloak realm setting). Cached in the
  `OAuth2AuthorizedClient` in Redis, refreshed transparently by Spring
  Security's `ServerOAuth2AuthorizedClientManager`.
- **Refresh token**: 30 minutes. Used to renew the access token when it
  expires. If the refresh token itself is expired, the BFF responds with
  401, clears the session and the SPA starts a fresh login.
- **ID token**: present after login but not forwarded to downstream
  services; only used for username display and logout hints.
- **Revocation on logout**: the BFF calls Keycloak's `/revoke` endpoint for
  the refresh token and deletes the Redis session entry atomically.

Tokens are **never** logged, returned in HTTP responses or stored in any
key outside the Spring Session namespace in Redis.

---

## What is deliberately *not* implemented

This is a reference / teaching project. The following items are typically
present in a production deployment but are intentionally out of scope here
to keep the focus on the BFF pattern:

- **HTTPS termination.** Local dev uses plain HTTP. In production you would
  terminate TLS at nginx or an upstream load balancer and flip
  `BFF_COOKIE_SECURE=true`.
- **Distributed rate limiting.** nginx is not configured with `limit_req`.
  Keycloak's brute-force protection covers the login endpoint but
  application endpoints are not rate-limited.
- **Web Application Firewall.** No ModSecurity / Coraza in the proxy path.
- **mTLS between BFF and downstream services.** The JWT bearer is the only
  authentication layer between them. In a zero-trust setup you would add
  mTLS on top.
- **Secret management.** The client secret is in `.env` for reproducibility.
  In production this belongs in Vault / a cloud KMS / a k8s secret.
- **Audit logging of security events** beyond what Spring Security and
  Keycloak emit by default.
- **Content Security Policy.** A strict CSP is environment-specific (it
  depends on which CDN, analytics and fonts you load) and is therefore not
  shipped in this reference. The nginx config has placeholders for the
  other security headers (`X-Frame-Options`, `X-Content-Type-Options`,
  `Referrer-Policy`, `Permissions-Policy`).

Each of these is a worthwhile addition, but each would double the scope of
the project without adding anything to the BFF pattern itself.
