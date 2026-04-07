package io.janda.userservice;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint that returns the profile of the authenticated user. */
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
