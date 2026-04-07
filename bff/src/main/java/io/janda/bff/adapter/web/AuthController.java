package io.janda.bff.adapter.web;

import io.janda.bff.adapter.web.dto.UserInfoResponse;
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
 * <p>{@code /login} starts the OIDC login flow by redirecting to the Spring Security login entry
 * point. {@code /logout/backchannel} accepts Keycloak back-channel logout notifications. {@code
 * /api/userinfo} returns the bare-minimum user info the SPA needs.
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
