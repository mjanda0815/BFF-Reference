package com.example.notificationservice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint that returns notifications for the authenticated user. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  @GetMapping("/me")
  public NotificationResponse forCurrentUser(@AuthenticationPrincipal Jwt principal) {
    String subject = principal.getSubject();
    Instant now = Instant.now();
    List<Notification> items =
        List.of(
            new Notification(
                "n-" + subject + "-1",
                "Welcome",
                "Welcome to the BFF reference dashboard.",
                now.minus(5, ChronoUnit.MINUTES)),
            new Notification(
                "n-" + subject + "-2",
                "New release available",
                "A new platform release has been deployed.",
                now.minus(2, ChronoUnit.HOURS)),
            new Notification(
                "n-" + subject + "-3",
                "Security update",
                "Please review the updated security policy.",
                now.minus(1, ChronoUnit.DAYS)));
    return new NotificationResponse(items.size(), items);
  }
}
