package io.janda.notificationservice;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(NotificationControllerTest.TestJwtConfig.class)
class NotificationControllerTest {

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
    mockMvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequestReturnsNotifications() throws Exception {
    mockMvc
        .perform(get("/api/notifications/me").with(jwt().jwt(j -> j.subject("user-42"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].title").exists());
  }
}
