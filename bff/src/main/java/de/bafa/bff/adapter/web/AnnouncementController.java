package de.bafa.bff.adapter.web;

import de.bafa.bff.application.DistributedWriteSagaOrchestrator;
import de.bafa.bff.application.SessionTokenService;
import de.bafa.bff.domain.model.AnnouncementCommand;
import de.bafa.bff.domain.model.AnnouncementSagaResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * BFF-side entry point for the distributed-write saga.
 *
 * <p>The SPA dispatches exactly one command here; the saga orchestrator owns every other
 * decision (step ordering, compensation, retry, timeout, log generation). The controller
 * itself is deliberately trivial — it resolves the session access token and hands off. Keeping
 * it thin is the entire architectural point of having a BFF: all workflow complexity collapses
 * to one endpoint, and the SPA does not need to know anything about token management,
 * service topology, or partial-failure semantics.
 *
 * <p><b>Für die Übernahme in ein neues Produkt:</b>
 *
 * <ul>
 *   <li><b>Pfad anpassen</b> (z. B. {@code /api/orders}) passend zur Produkt-Domäne; die
 *       Kontrakt-Form (POST-Command, rendered log) bleibt gleich.
 *   <li><b>Request-Body-Typ austauschen</b> durch ein produktspezifisches Command-Record mit
 *       allen fachlichen Feldern.
 *   <li><b>Einen eigenen Orchestrator</b> schreiben, der die neuen Schritte kennt — dieser
 *       Controller bleibt strukturgleich, er ruft nur eine andere Saga auf.
 * </ul>
 *
 * <p>CSRF: Der Endpunkt ist durch den Double-Submit-Token aus {@code SecurityConfig}
 * geschützt; neue Write-Endpunkte erben das automatisch.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

  private final DistributedWriteSagaOrchestrator orchestrator;
  private final SessionTokenService sessionTokenService;

  public AnnouncementController(
      DistributedWriteSagaOrchestrator orchestrator, SessionTokenService sessionTokenService) {
    this.orchestrator = orchestrator;
    this.sessionTokenService = sessionTokenService;
  }

  @PostMapping
  public Mono<ResponseEntity<AnnouncementSagaResult>> execute(
      @AuthenticationPrincipal OidcUser principal,
      @RequestBody AnnouncementCommand command,
      ServerWebExchange exchange) {
    if (principal == null) {
      return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
    return sessionTokenService
        .currentAccessToken(principal, exchange)
        .switchIfEmpty(Mono.error(new IllegalStateException("No access token in session")))
        .flatMap(token -> orchestrator.execute(command, token))
        .map(ResponseEntity::ok);
  }
}
