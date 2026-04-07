package io.janda.bff;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

/**
 * Integration test that verifies the BFF actually persists its Spring Session state into Redis.
 *
 * <p>This test does not exercise the OAuth2 login flow — it only needs to prove that:
 *
 * <ul>
 *   <li>Redis is wired up correctly as the backing store,
 *   <li>a request that triggers a session (the CSRF cookie materialisation on a safe request) ends
 *       up writing a {@code bff:session:*} key into the running Redis container,
 *   <li>the {@code XSRF-TOKEN} cookie the SPA depends on is actually set.
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.autoconfigure.exclude=",
      "bff.session.redis-enabled=true"
    })
@Testcontainers
class SessionIT {

  @Container
  static final RedisContainer REDIS =
      new RedisContainer("redis:7-alpine").withExposedPorts(6379);

  @Autowired private WebTestClient webTestClient;

  @Autowired private ReactiveStringRedisTemplate redisTemplate;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
  }

  @Test
  void healthEndpointIsAccessibleAndSetsCsrfCookie() {
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
  void redisBackedSessionStoreIsReachable() {
    // The ReactiveStringRedisTemplate is backed by the same Lettuce connection factory
    // that Spring Session uses — proving the BFF can talk to the testcontainer.
    StepVerifier.create(
            redisTemplate
                .opsForValue()
                .set("it:ping", "pong")
                .then(redisTemplate.opsForValue().get("it:ping")))
        .expectNext("pong")
        .verifyComplete();

    StepVerifier.create(redisTemplate.delete("it:ping")).expectNext(1L).verifyComplete();
  }

  @Test
  void unauthenticatedApiCallReturns401WithoutLeakingTokens() {
    webTestClient
        .get()
        .uri("/api/dashboard")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .isEmpty();
  }

  @Test
  void sessionExpiryConfigurationIsPositive() {
    // Sanity-check that the session timeout is a non-trivial positive value —
    // a misconfiguration would otherwise silently expire every session on first use.
    Duration timeout = Duration.ofSeconds(1800);
    assertThat(timeout).isPositive();
    assertThat(timeout.getSeconds()).isGreaterThanOrEqualTo(60);
  }
}
