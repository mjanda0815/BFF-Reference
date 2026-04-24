package de.bafa.userservice;

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
 * Web-layer tests for the distributed-write saga endpoints on the user-service.
 *
 * <p>Pins the three user-visible properties:
 *
 * <ol>
 *   <li>The endpoint is authenticated (no anonymous access).
 *   <li>A valid command persists a subscription and echoes the stored record.
 *   <li>{@code forceFail=true} triggers a reproducible 500 so the BFF saga can exercise the
 *       compensation path during demos.
 * </ol>
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
            post("/api/users/me/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-1\",\"message\":\"hi\",\"forceFail\":false}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedPostStoresSubscription() throws Exception {
    mockMvc
        .perform(
            post("/api/users/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-1\",\"message\":\"hi\",\"forceFail\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.announcementId").value("ann-1"))
        .andExpect(jsonPath("$.userId").value("user-1"))
        .andExpect(jsonPath("$.message").value("hi"));
  }

  @Test
  void forceFailReturns500() throws Exception {
    mockMvc
        .perform(
            post("/api/users/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-2\",\"message\":\"x\",\"forceFail\":true}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void deleteRemovesPreviouslyStoredSubscription() throws Exception {
    // First POST so a record exists to delete.
    mockMvc
        .perform(
            post("/api/users/me/announcements")
                .with(jwt().jwt(j -> j.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcementId\":\"ann-3\",\"message\":\"z\",\"forceFail\":false}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/users/me/announcements/ann-3").with(jwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenNothingToCompensate() throws Exception {
    mockMvc
        .perform(delete("/api/users/me/announcements/unknown").with(jwt()))
        .andExpect(status().isNotFound());
  }
}
