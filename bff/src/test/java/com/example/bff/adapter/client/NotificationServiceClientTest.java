package com.example.bff.adapter.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.bff.domain.model.NotificationOverview;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class NotificationServiceClientTest {

  private MockWebServer server;
  private NotificationServiceClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    client = new NotificationServiceClient(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void parsesNotificationResponse() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(
                "{\"unreadCount\":2,\"items\":[{\"id\":\"n1\",\"title\":\"hi\",\"message\":\"m\",\"timestamp\":\"2024-01-01T00:00:00Z\"}]}"));
    StepVerifier.create(client.getNotifications("u1", "tok"))
        .assertNext(
            n -> {
              assertEquals(2, n.unreadCount());
              assertEquals(1, n.items().size());
              assertEquals("hi", n.items().get(0).title());
            })
        .verifyComplete();
  }

  @Test
  void propagatesUpstreamError() {
    server.enqueue(new MockResponse().setResponseCode(503));
    StepVerifier.create(client.getNotifications("u1", "tok")).expectError().verify();
  }

  @Test
  void emptyResponseFallback() {
    NotificationOverview empty = NotificationOverview.empty();
    assertEquals(0, empty.unreadCount());
    assertEquals(0, empty.items().size());
  }
}
