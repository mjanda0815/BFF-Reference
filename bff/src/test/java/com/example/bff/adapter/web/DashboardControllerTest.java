package com.example.bff.adapter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.bff.application.DashboardAggregationService;
import com.example.bff.application.SessionTokenService;
import com.example.bff.domain.model.DashboardData;
import com.example.bff.domain.model.NotificationOverview;
import com.example.bff.domain.model.UserProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DashboardControllerTest {

  private DashboardAggregationService aggregationService;
  private SessionTokenService sessionTokenService;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    aggregationService = mock(DashboardAggregationService.class);
    sessionTokenService = mock(SessionTokenService.class);
    controller = new DashboardController(aggregationService, sessionTokenService);
  }

  @Test
  void returnsAggregatedDashboard() {
    OidcUser principal = mock(OidcUser.class);
    when(principal.getSubject()).thenReturn("u1");
    when(sessionTokenService.currentAccessToken(any(), any())).thenReturn(Mono.just("tok"));
    DashboardData expected =
        new DashboardData(UserProfile.empty(), NotificationOverview.empty(), List.of());
    when(aggregationService.buildDashboard(anyString(), anyString())).thenReturn(Mono.just(expected));

    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/dashboard"));

    StepVerifier.create(controller.dashboard(principal, exchange))
        .assertNext(
            response -> {
              assertEquals(HttpStatus.OK, response.getStatusCode());
              assertEquals(expected, response.getBody());
            })
        .verifyComplete();
  }

  @Test
  void returnsUnauthorizedWhenPrincipalMissing() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/dashboard"));
    StepVerifier.create(controller.dashboard(null, exchange))
        .assertNext(response -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()))
        .verifyComplete();
  }

  @Test
  void failsWhenNoAccessToken() {
    OidcUser principal = mock(OidcUser.class);
    when(principal.getSubject()).thenReturn("u1");
    when(sessionTokenService.currentAccessToken(any(), any())).thenReturn(Mono.empty());

    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/dashboard"));

    StepVerifier.create(controller.dashboard(principal, exchange))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
