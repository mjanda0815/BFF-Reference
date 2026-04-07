package io.janda.activityservice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint that returns the recent activity for the authenticated user. */
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

  @GetMapping("/me")
  public List<ActivityEvent> recentForCurrentUser(@AuthenticationPrincipal Jwt principal) {
    String subject = principal.getSubject();
    Instant now = Instant.now();
    return List.of(
        new ActivityEvent(
            "a-" + subject + "-1", "LOGIN", "session", now.minus(1, ChronoUnit.MINUTES)),
        new ActivityEvent(
            "a-" + subject + "-2", "VIEW", "dashboard", now.minus(10, ChronoUnit.MINUTES)),
        new ActivityEvent(
            "a-" + subject + "-3", "UPDATE", "profile", now.minus(2, ChronoUnit.HOURS)),
        new ActivityEvent(
            "a-" + subject + "-4", "DOWNLOAD", "report-2024.pdf", now.minus(1, ChronoUnit.DAYS)));
  }
}
