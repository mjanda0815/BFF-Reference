package de.bafa.bff.adapter.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ActivityAnnouncementWriteClientTest {

  private MockWebServer server;
  private ActivityAnnouncementWriteClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    client = new ActivityAnnouncementWriteClient(webClient);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void logAnnouncementPostsToAnnouncementsEndpointWithBearerToken() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(200));

    StepVerifier.create(client.logAnnouncement("ann-1", "Hello world", false, "tok"))
        .verifyComplete();

    RecordedRequest request = server.takeRequest();
    assertEquals("POST", request.getMethod());
    assertEquals("/api/activity/me/announcements", request.getPath());
    assertEquals("Bearer tok", request.getHeader("Authorization"));
    String body = request.getBody().readUtf8();
    assertTrue(body.contains("\"announcementId\":\"ann-1\""), body);
    assertTrue(body.contains("\"message\":\"Hello world\""), body);
    assertTrue(body.contains("\"forceFail\":false"), body);
  }

  @Test
  void compensateDeletesTheAnnouncement() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(204));

    StepVerifier.create(client.compensate("ann-1", "tok")).verifyComplete();

    RecordedRequest request = server.takeRequest();
    assertEquals("DELETE", request.getMethod());
    assertEquals("/api/activity/me/announcements/ann-1", request.getPath());
  }

  @Test
  void propagatesUpstreamFailure() {
    server.enqueue(new MockResponse().setResponseCode(500));
    StepVerifier.create(client.logAnnouncement("ann-1", "msg", false, "tok"))
        .expectError()
        .verify();
  }
}
