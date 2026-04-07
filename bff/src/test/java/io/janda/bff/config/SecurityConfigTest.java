package io.janda.bff.config;

import io.janda.bff.BffApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.test.web.reactive.server.WebTestClient;


@SpringBootTest(
    classes = BffApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

  @Autowired private WebTestClient webTestClient;

  @MockBean private ReactiveOAuth2AuthorizedClientService authorizedClientService;

  @Test
  void unauthorizedApiCallReturns401() {
    webTestClient.get().uri("/api/dashboard").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void unauthorizedNonApiCallRedirectsToLogin() {
    webTestClient
        .get()
        .uri("/some-page")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals("Location", "/oauth2/authorization/keycloak");
  }

  @Test
  void healthEndpointIsPublic() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  @Test
  void csrfCookieIsSetOnSafeRequests() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectCookie()
        .exists("XSRF-TOKEN");
  }

  @Test
  void unauthenticatedPostIsRejected() {
    // POST without auth and without CSRF should be rejected (401 because auth runs first).
    webTestClient
        .post()
        .uri("/api/dashboard")
        .exchange()
        .expectStatus()
        .value(
            status ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    status == 401 || status == 403, "expected 401/403 but was " + status));
  }

  @Test
  void corsPreflightFromAllowedOriginIsAccepted() {
    webTestClient
        .options()
        .uri("/api/dashboard")
        .header("Origin", "http://localhost")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost");
  }
}
