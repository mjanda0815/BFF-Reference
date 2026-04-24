package de.bafa.notificationservice;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for the distributed-write saga endpoints on the notification-service.
 *
 * <p>Mirrors the user-service test: happy path, forced failure, compensate, 404 on unknown id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AnnouncementControllerTest.TestJwtConfig.class)
class AnnouncementControllerTest {

  @Autowired private MockMvc mockMvc;

  @TestConfiguration
  static class TestJwtConfig {
    @Bean
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }

  @Test
  void unauthenticatedPostIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/me/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"announcementId\":\"ann-1\",\"title\":\"T\",\"message\":\"M\",\"forceFail\":false}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedPostStoresBroadcast() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"announcementId\":\"ann-1\",\"title\":\"T\",\"message\":\"M\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("ann-1"))
        .andExpect(jsonPath("$.title").value("T"))
        .andExpect(jsonPath("$.message").value("M"));
  }

  @Test
  void missingTitleAndMessageGetDefaults() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-defaults\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Announcement"));
  }

  @Test
  void forceFailReturns500() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"announcementId\":\"ann-2\",\"title\":\"T\",\"message\":\"x\",\"forceFail\":true}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void deleteRemovesPreviouslyStoredBroadcast() throws Exception {
    mockMvc
        .perform(
            post("/api/notifications/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"announcementId\":\"ann-3\",\"title\":\"T\",\"message\":\"z\",\"forceFail\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/notifications/me/announcements/ann-3").with(jwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenNothingToCompensate() throws Exception {
    mockMvc
        .perform(delete("/api/notifications/me/announcements/unknown").with(jwt()))
        .andExpect(status().isNotFound());
  }
}
