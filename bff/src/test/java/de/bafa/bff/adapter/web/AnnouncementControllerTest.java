package de.bafa.bff.adapter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.bafa.bff.application.DistributedWriteSagaOrchestrator;
import de.bafa.bff.application.SessionTokenService;
import de.bafa.bff.domain.model.AnnouncementCommand;
import de.bafa.bff.domain.model.AnnouncementSagaResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Controller-level tests for the BFF's saga entry point, POST /api/announcements.
 *
 * <p>The controller itself is intentionally thin — it does three things and nothing else:
 *
 * <ol>
 *   <li>reject unauthenticated requests with 401,
 *   <li>resolve the session access token via {@link SessionTokenService},
 *   <li>hand off to {@link DistributedWriteSagaOrchestrator} and return whatever it produced.
 * </ol>
 *
 * <p>Everything about the saga (ordering, compensation, logging) is covered by
 * {@code DistributedWriteSagaOrchestratorTest}; here we only pin the controller contract.
 */
class AnnouncementControllerTest {

  private DistributedWriteSagaOrchestrator orchestrator;
  private SessionTokenService sessionTokenService;
  private AnnouncementController controller;

  @BeforeEach
  void setUp() {
    orchestrator = mock(DistributedWriteSagaOrchestrator.class);
    sessionTokenService = mock(SessionTokenService.class);
    controller = new AnnouncementController(orchestrator, sessionTokenService);
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.post("/api/announcements"));
  }

  @Test
  void returnsUnauthorizedWhenPrincipalIsMissing() {
    StepVerifier.create(
            controller.execute(null, new AnnouncementCommand("hello", null), exchange()))
        .assertNext(
            response -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()))
        .verifyComplete();
  }

  /**
   * Authenticated path: the controller must pull the access token from the session token
   * service, forward it to the orchestrator, and return the result body unchanged.
   */
  @Test
  void dispatchesTheCommandToTheOrchestratorWithTheSessionToken() {
    OidcUser principal = mock(OidcUser.class);
    when(principal.getSubject()).thenReturn("u1");
    when(sessionTokenService.currentAccessToken(any(), any())).thenReturn(Mono.just("the-token"));

    AnnouncementCommand command = new AnnouncementCommand("Test", "notification");
    AnnouncementSagaResult result =
        new AnnouncementSagaResult(
            "ann-1", AnnouncementSagaResult.OUTCOME_COMPENSATED, "Test", List.of());
    when(orchestrator.execute(eq(command), eq("the-token"))).thenReturn(Mono.just(result));

    StepVerifier.create(controller.execute(principal, command, exchange()))
        .assertNext(
            response -> {
              assertEquals(HttpStatus.OK, response.getStatusCode());
              assertSame(result, response.getBody());
            })
        .verifyComplete();
  }

  /**
   * If the session has no access token (e.g. after a forced Redis eviction) the controller
   * surfaces that as an error so the caller — and the global error handler — can react.
   */
  @Test
  void failsWhenNoAccessTokenInSession() {
    OidcUser principal = mock(OidcUser.class);
    when(sessionTokenService.currentAccessToken(any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(
            controller.execute(principal, new AnnouncementCommand("x", null), exchange()))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
