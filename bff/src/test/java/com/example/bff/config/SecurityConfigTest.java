package com.example.bff.config;

import com.example.bff.BffApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

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
  void postWithoutCsrfTokenIsRejected() {
    webTestClient
        .mutateWith(mockOidcLogin())
        .post()
        .uri("/api/dashboard")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void postWithCsrfTokenIsAccepted() {
    webTestClient
        .mutateWith(mockOidcLogin())
        .mutateWith(csrf())
        .post()
        .uri("/api/dashboard")
        .exchange()
        // No POST handler exists, but the CSRF filter should not block — expect 405 or 404.
        .expectStatus()
        .value(status -> org.junit.jupiter.api.Assertions.assertNotEquals(403, status));
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
