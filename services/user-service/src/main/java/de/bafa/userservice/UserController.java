package de.bafa.userservice;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint that returns the profile of the authenticated user.
 *
 * <p><b>Blueprint note:</b> this controller synthesises a profile from JWT claims instead of
 * hitting a database, because its sole purpose in the reference is to prove the token-validation
 * and BFF-fan-out plumbing. A real downstream service would load the profile from its own store;
 * the shape of the endpoint (bearer-protected, derives the user id from the JWT subject) stays
 * the same.
 *
 * <p>{@code preferred_username} is the usual Keycloak claim; if it is absent we fall back to the
 * raw subject so this works even with minimally configured identity providers.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

  @GetMapping("/me")
  public UserProfile me(@AuthenticationPrincipal Jwt principal) {
    String subject = principal.getSubject();
    String preferred = principal.getClaimAsString("preferred_username");
    String displayName = preferred != null ? preferred : subject;
    String avatar =
        "https://api.dicebear.com/7.x/initials/svg?seed=" + displayName.replace(" ", "+");
    return new UserProfile(subject, displayName, "user", avatar);
  }
}
