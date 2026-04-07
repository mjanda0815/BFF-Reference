package com.example.bff.adapter.web;

import com.example.bff.application.DashboardAggregationService;
import com.example.bff.application.SessionTokenService;
import com.example.bff.domain.model.DashboardData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** REST controller exposing the aggregated dashboard endpoint to the SPA. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardAggregationService aggregationService;
  private final SessionTokenService sessionTokenService;

  public DashboardController(
      DashboardAggregationService aggregationService, SessionTokenService sessionTokenService) {
    this.aggregationService = aggregationService;
    this.sessionTokenService = sessionTokenService;
  }

  @GetMapping
  public Mono<ResponseEntity<DashboardData>> dashboard(
      @AuthenticationPrincipal OidcUser principal, ServerWebExchange exchange) {
    if (principal == null) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
    String userId = principal.getSubject();
    return sessionTokenService
        .currentAccessToken(principal, exchange)
        .switchIfEmpty(Mono.error(new IllegalStateException("No access token in session")))
        .flatMap(token -> aggregationService.buildDashboard(userId, token))
        .map(ResponseEntity::ok);
  }
}
