package de.bafa.activityservice;

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
 * Web-layer tests for the distributed-write saga endpoints on the activity-service.
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
            post("/api/activity/me/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-1\",\"message\":\"hi\",\"forceFail\":false}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedPostStoresActivity() throws Exception {
    mockMvc
        .perform(
            post("/api/activity/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-1\",\"message\":\"hi\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("ann-1"))
        .andExpect(jsonPath("$.action").value("ANNOUNCEMENT"))
        .andExpect(jsonPath("$.resource").value("hi"));
  }

  @Test
  void missingMessageGetsFallbackResource() throws Exception {
    mockMvc
        .perform(
            post("/api/activity/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-default\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resource").value("announcement"));
  }

  @Test
  void forceFailReturns500() throws Exception {
    mockMvc
        .perform(
            post("/api/activity/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-2\",\"message\":\"x\",\"forceFail\":true}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void deleteRemovesPreviouslyStoredActivity() throws Exception {
    mockMvc
        .perform(
            post("/api/activity/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-3\",\"message\":\"z\",\"forceFail\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/activity/me/announcements/ann-3").with(jwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenNothingToCompensate() throws Exception {
    mockMvc
        .perform(delete("/api/activity/me/announcements/unknown").with(jwt()))
        .andExpect(status().isNotFound());
  }

  /**
   * Idempotency contract: duplicate POST with the same {@code announcementId} returns the
   * originally stored activity event unchanged. Lets the BFF saga's retry loop replay a
   * transient failure without producing duplicate activity rows.
   */
  @Test
  void duplicatePostReturnsOriginalRecordUnchanged() throws Exception {
    mockMvc
        .perform(
            post("/api/activity/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"announcementId\":\"ann-idem\",\"message\":\"first\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resource").value("first"));

    mockMvc
        .perform(
            post("/api/activity/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"announcementId\":\"ann-idem\",\"message\":\"second\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resource").value("first"));
  }
}
