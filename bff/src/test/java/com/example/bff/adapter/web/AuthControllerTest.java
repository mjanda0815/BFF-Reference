package com.example.bff.adapter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.bff.adapter.web.dto.UserInfoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

class AuthControllerTest {

  private final AuthController controller = new AuthController();
  private final WebTestClient client = WebTestClient.bindToController(controller).build();

  @Test
  void loginRedirectsToOAuth2Authorization() {
    client
        .get()
        .uri("/login")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals("Location", "/oauth2/authorization/keycloak");
  }

  @Test
  void backchannelLogoutReturnsOk() {
    client.post().uri("/logout/backchannel").exchange().expectStatus().isOk();
  }

  @Test
  void userInfoReturnsMappedPrincipal() {
    OidcUser principal = mock(OidcUser.class);
    when(principal.getSubject()).thenReturn("u1");
    when(principal.getPreferredUsername()).thenReturn("alice");
    when(principal.getEmail()).thenReturn("alice@example.com");

    StepVerifier.create(controller.userInfo(principal))
        .assertNext(
            response -> {
              UserInfoResponse body = response.getBody();
              assertEquals(HttpStatus.OK, response.getStatusCode());
              org.junit.jupiter.api.Assertions.assertNotNull(body);
              assertEquals("u1", body.userId());
              assertEquals("alice", body.displayName());
              assertEquals("alice@example.com", body.email());
            })
        .verifyComplete();
  }

  @Test
  void userInfoReturnsUnauthorizedWhenPrincipalMissing() {
    StepVerifier.create(controller.userInfo(null))
        .assertNext(
            response ->
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()))
        .verifyComplete();
  }

  @Test
  void userInfoFallsBackToSubjectWhenPreferredUsernameMissing() {
    OidcUser principal = mock(OidcUser.class);
    when(principal.getSubject()).thenReturn("u-7");
    when(principal.getPreferredUsername()).thenReturn(null);
    when(principal.getEmail()).thenReturn(null);

    StepVerifier.create(controller.userInfo(principal))
        .assertNext(
            response -> {
              ResponseEntity<UserInfoResponse> typed = response;
              UserInfoResponse body = typed.getBody();
              org.junit.jupiter.api.Assertions.assertNotNull(body);
              assertEquals("u-7", body.displayName());
              assertEquals("", body.email());
            })
        .verifyComplete();
  }
}
