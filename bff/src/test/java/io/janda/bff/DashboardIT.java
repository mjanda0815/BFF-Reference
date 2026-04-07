package io.janda.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.redis.testcontainers.RedisContainer;
import io.janda.bff.application.SessionTokenService;
import io.janda.bff.domain.model.DashboardData;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * End-to-end integration test for the aggregated {@code /api/dashboard} endpoint.
 *
 * <p>Brings up:
 *
 * <ul>
 *   <li>a real Redis via Testcontainers, backing Spring Session,
 *   <li>three {@link MockWebServer} instances standing in for the downstream microservices,
 *   <li>the full Spring context with security and reactive OAuth2 client wired up.
 * </ul>
 *
 * <p>Covers the happy path (all three services return data), a partial-failure path (one service
 * returns 500 — the dashboard must still succeed with an empty section) and the unauthenticated
 * case (no mocked login — the endpoint must return 401).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.autoconfigure.exclude=",
      "bff.session.redis-enabled=true"
    })
@Testcontainers
class DashboardIT {

  @Container
  static final RedisContainer REDIS =
      new RedisContainer("redis:7-alpine").withExposedPorts(6379);

  private static final MockWebServer USER_SERVICE = startServer();
  private static final MockWebServer NOTIFICATION_SERVICE = startServer();
  private static final MockWebServer ACTIVITY_SERVICE = startServer();

  @Autowired private ApplicationContext applicationContext;

  @MockBean private SessionTokenService sessionTokenService;

  private WebTestClient anonymousClient() {
    return WebTestClient.bindToApplicationContext(applicationContext)
        .apply(springSecurity())
        .configureClient()
        .build();
  }

  private WebTestClient authenticatedClient(String subject) {
    return WebTestClient.bindToApplicationContext(applicationContext)
        .apply(springSecurity())
        .apply(mockOidcLogin().idToken(token -> token.subject(subject)))
        .apply(csrf())
        .configureClient()
        .build();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    registry.add("bff.user-service-url", () -> baseUrl(USER_SERVICE));
    registry.add("bff.notification-service-url", () -> baseUrl(NOTIFICATION_SERVICE));
    registry.add("bff.activity-service-url", () -> baseUrl(ACTIVITY_SERVICE));
  }

  @AfterAll
  static void shutdownServers() throws IOException {
    USER_SERVICE.shutdown();
    NOTIFICATION_SERVICE.shutdown();
    ACTIVITY_SERVICE.shutdown();
  }

  @Test
  void aggregatesDataFromAllThreeServices() {
    when(sessionTokenService.currentAccessToken(any(), any()))
        .thenReturn(Mono.just("fake-access-token"));

    USER_SERVICE.enqueue(
        jsonResponse(
            "{\"userId\":\"u-1\",\"displayName\":\"Alice\",\"role\":\"admin\",\"avatarUrl\":\"https://a/u1\"}"));
    NOTIFICATION_SERVICE.enqueue(
        jsonResponse(
            "{\"unreadCount\":2,\"items\":[{\"id\":\"n-1\",\"title\":\"Welcome\",\"message\":\"hi\",\"timestamp\":\"2026-04-06T10:00:00Z\"}]}"));
    ACTIVITY_SERVICE.enqueue(
        jsonResponse(
            "[{\"id\":\"a-1\",\"action\":\"login\",\"resource\":\"portal\",\"timestamp\":\"2026-04-06T09:59:00Z\"}]"));

    authenticatedClient("u-1")
        .get()
        .uri("/api/dashboard")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(DashboardData.class)
        .value(
            data -> {
              assertThat(data.profile().displayName()).isEqualTo("Alice");
              assertThat(data.profile().role()).isEqualTo("admin");
              assertThat(data.notifications().unreadCount()).isEqualTo(2);
              assertThat(data.notifications().items()).hasSize(1);
              assertThat(data.activity()).hasSize(1);
              assertThat(data.activity().get(0).action()).isEqualTo("login");
            });
  }

  @Test
  void returnsPartialDashboardWhenOneServiceFails() {
    when(sessionTokenService.currentAccessToken(any(), any()))
        .thenReturn(Mono.just("fake-access-token"));

    USER_SERVICE.enqueue(new MockResponse().setResponseCode(500));
    NOTIFICATION_SERVICE.enqueue(
        jsonResponse("{\"unreadCount\":0,\"items\":[]}"));
    ACTIVITY_SERVICE.enqueue(jsonResponse("[]"));

    authenticatedClient("u-2")
        .get()
        .uri("/api/dashboard")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(DashboardData.class)
        .value(
            data -> {
              // user-service failed → empty profile fallback
              assertThat(data.profile().displayName()).isEmpty();
              assertThat(data.notifications().unreadCount()).isZero();
              assertThat(data.activity()).isEmpty();
            });
  }

  @Test
  void unauthenticatedRequestReturns401() {
    anonymousClient()
        .get()
        .uri("/api/dashboard")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .setBody(body);
  }

  private static String baseUrl(MockWebServer server) {
    String url = server.url("/").toString();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static MockWebServer startServer() {
    try {
      MockWebServer server = new MockWebServer();
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start MockWebServer", e);
    }
  }
}
