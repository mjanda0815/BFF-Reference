package de.bafa.userservice;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UserControllerTest.TestJwtConfig.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @TestConfiguration
  static class TestJwtConfig {
    @Bean
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequestReturnsProfile() throws Exception {
    mockMvc
        .perform(
            get("/api/users/me")
                .with(jwt().jwt(j -> j.subject("user-123").claim("preferred_username", "alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-123"))
        .andExpect(jsonPath("$.displayName").value("alice"))
        .andExpect(jsonPath("$.role").value("user"))
        .andExpect(jsonPath("$.avatarUrl").exists());
  }

  @Test
  void healthEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  /**
   * Covers the fallback branch in {@link UserController#me} where {@code preferred_username} is
   * absent from the JWT and the subject claim must be used as display name. Kept as a dedicated
   * test so the branch is documented explicitly — a team copying the service should see the
   * claim fallback is an intentional, tested behavior.
   */
  @Test
  void missingPreferredUsernameFallsBackToSubject() throws Exception {
    mockMvc
        .perform(get("/api/users/me").with(jwt().jwt(j -> j.subject("user-xyz"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-xyz"))
        .andExpect(jsonPath("$.displayName").value("user-xyz"));
  }
}
