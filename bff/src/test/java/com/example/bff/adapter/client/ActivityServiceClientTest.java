package com.example.bff.adapter.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ActivityServiceClientTest {

  private MockWebServer server;
  private ActivityServiceClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    client = new ActivityServiceClient(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void parsesActivityList() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(
                "[{\"id\":\"a1\",\"action\":\"LOGIN\",\"resource\":\"session\",\"timestamp\":\"2024-01-01T00:00:00Z\"}]"));
    StepVerifier.create(client.getRecentActivity("u1", "tok"))
        .assertNext(
            list -> {
              assertEquals(1, list.size());
              assertEquals("LOGIN", list.get(0).action());
            })
        .verifyComplete();
  }

  @Test
  void propagatesError() {
    server.enqueue(new MockResponse().setResponseCode(500));
    StepVerifier.create(client.getRecentActivity("u1", "tok")).expectError().verify();
  }
}
