# ADR-002: Authenticate the SPA with a session cookie, not a browser-held token

- **Status:** Accepted
- **Date:** 2026-04-06

## Context

Once we decided to use a BFF (ADR-001), we still had to choose how the SPA
authenticates itself against the BFF on each request. Two common options:

1. **Bearer token in the `Authorization` header**, with the token either
   obtained from Keycloak directly or minted by the BFF. The SPA stores it
   in memory or `localStorage`.
2. **Opaque session cookie** set by the BFF after login, automatically
   attached by the browser on every subsequent request.

## Decision

We use an **opaque `HttpOnly` session cookie** (`SESSION`) issued by Spring
Session. The SPA never holds any kind of token.

## Consequences

### Positive

- **XSS cannot read the cookie.** `HttpOnly` removes the cookie from the
  reach of `document.cookie`. An XSS can still *act* as the user for the
  duration of the attack, but cannot *exfiltrate* the session id for use
  from an attacker-controlled machine outside the browser.
- **Automatic attachment.** The browser attaches the cookie on every
  same-origin request. The SPA's HTTP layer is trivially simple:
  `withCredentials: true` and done.
- **Cheap rotation / invalidation.** The server owns the session. Logout,
  timeout or admin revocation is a single Redis `DEL` — no revocation list
  or token blacklist needed.
- **No token parsing in the frontend.** No JWT decoding, no clock skew
  handling, no "is my token about to expire" logic.

### Negative

- **Requires same-origin deployment** (SPA and BFF share an origin via
  nginx reverse proxy). Cross-origin cookie setups need `SameSite=None;
  Secure`, which weakens CSRF posture and is avoided here.
- **CSRF protection is mandatory.** Because the browser attaches the
  cookie automatically, we must defend against cross-site request forgery.
  See the security concept for the double-submit token implementation.

### Rejected alternative: access token in `Authorization` header from the SPA

- Exposes tokens to XSS (either in `localStorage` or in JS memory that the
  attacker's script can reach).
- Makes logout harder: revoking a JWT that is already in the client's hands
  requires a blacklist or very short lifetimes plus a refresh flow, which
  brings its own storage problems.
- Forces CORS + `Authorization` header wiring in the frontend for every
  call, with no real benefit in a same-origin deployment.
