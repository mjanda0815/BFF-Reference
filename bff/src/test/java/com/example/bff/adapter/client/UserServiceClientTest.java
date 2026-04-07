package com.example.bff.adapter.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.bff.domain.model.UserProfile;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class UserServiceClientTest {

  private MockWebServer server;
  private UserServiceClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    client = new UserServiceClient(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void returnsParsedUserProfile() throws InterruptedException {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(
                "{\"userId\":\"u1\",\"displayName\":\"Alice\",\"role\":\"user\",\"avatarUrl\":\"https://a/u1\"}"));

    StepVerifier.create(client.getUserProfile("u1", "token-xyz"))
        .assertNext(
            profile -> {
              assertEquals("u1", profile.userId());
              assertEquals("Alice", profile.displayName());
              assertEquals("user", profile.role());
              assertEquals("https://a/u1", profile.avatarUrl());
            })
        .verifyComplete();

    RecordedRequest request = server.takeRequest();
    assertEquals("GET", request.getMethod());
    assertEquals("/api/users/me", request.getPath());
    assertEquals("Bearer token-xyz", request.getHeader("Authorization"));
  }

  @Test
  void propagatesUpstreamFailure() {
    server.enqueue(new MockResponse().setResponseCode(500));
    StepVerifier.create(client.getUserProfile("u1", "tok"))
        .expectErrorSatisfies(ex -> assertNotNull(ex.getMessage()))
        .verify();
  }

  @Test
  void emptyResponseBodyCompletesEmpty() {
    server.enqueue(new MockResponse().setResponseCode(204));
    StepVerifier.create(client.getUserProfile("u1", "tok")).verifyComplete();
  }

  @Test
  void mapsEmptyProfileFallback() {
    UserProfile empty = UserProfile.empty();
    assertEquals("", empty.userId());
    assertEquals("", empty.displayName());
  }
}
