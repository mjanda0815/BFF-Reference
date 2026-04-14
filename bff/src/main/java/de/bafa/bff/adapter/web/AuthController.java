package de.bafa.bff.adapter.web;

import de.bafa.bff.adapter.web.dto.UserInfoResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Authentication helper endpoints for the SPA.
 *
 * <p><b>Endpoints and their contract with the frontend:</b>
 *
 * <ul>
 *   <li>{@code GET /login} — convenience redirect that kicks off the OIDC Authorization Code
 *       Flow. The SPA calls this whenever an API response comes back 401.
 *   <li>{@code POST /logout/backchannel} — OIDC back-channel logout notification endpoint for
 *       Keycloak. Accepts the notification; actual session cleanup happens on the session's next
 *       touch / expiry. A full implementation would parse the JWT logout_token and proactively
 *       kill the matching session in Redis; this reference keeps it minimal.
 *   <li>{@code GET /api/userinfo} — returns the minimum user info the SPA needs to render a
 *       "Hello, X" header. Deliberately <em>never</em> echoes any OAuth2 token.
 * </ul>
 *
 * <p><b>Blueprint note:</b> the login endpoint is a thin wrapper around Spring Security's {@code
 * /oauth2/authorization/{id}} to give the SPA a stable URL that does not leak the registration id.
 */
@RestController
public class AuthController {

  /** Convenience login endpoint that triggers the OIDC authorization code flow. */
  @GetMapping("/login")
  public Mono<ResponseEntity<Void>> login() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("/oauth2/authorization/keycloak"))
            .build());
  }

  /**
   * Back-channel logout endpoint. Keycloak posts a logout token here when the session is killed
   * out-of-band. We accept the request and rely on session expiry / front-channel cleanup.
   */
  @PostMapping("/logout/backchannel")
  public Mono<ResponseEntity<Void>> backchannelLogout() {
    return Mono.just(ResponseEntity.ok().build());
  }

  /** Returns the minimal user info needed by the SPA — never any tokens. */
  @GetMapping("/api/userinfo")
  public Mono<ResponseEntity<UserInfoResponse>> userInfo(
      @AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
    String userId = principal.getSubject();
    String displayName =
        principal.getPreferredUsername() != null ? principal.getPreferredUsername() : userId;
    String email = principal.getEmail() != null ? principal.getEmail() : "";
    return Mono.just(ResponseEntity.ok(new UserInfoResponse(userId, displayName, email)));
  }
}
