package com.example.bff.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SessionInvalidationHandlerTest {

  private ReactiveOAuth2AuthorizedClientService service;
  private SessionInvalidationHandler handler;

  @BeforeEach
  void setUp() {
    service = mock(ReactiveOAuth2AuthorizedClientService.class);
    handler = new SessionInvalidationHandler(service);
  }

  @Test
  void removesAuthorizedClientForOidcUser() {
    OidcUser user = mock(OidcUser.class);
    when(user.getName()).thenReturn("alice");
    when(service.removeAuthorizedClient(anyString(), anyString())).thenReturn(Mono.empty());
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(user, "n/a", java.util.List.of());

    StepVerifier.create(handler.logout(mock(WebFilterExchange.class), authentication))
        .verifyComplete();

    verify(service).removeAuthorizedClient("keycloak", "alice");
  }

  @Test
  void completesWhenAuthenticationIsNull() {
    StepVerifier.create(handler.logout(mock(WebFilterExchange.class), null)).verifyComplete();
  }

  @Test
  void completesWhenPrincipalIsNotOidcUser() {
    Authentication authentication =
        new UsernamePasswordAuthenticationToken("not-oidc", "n/a", java.util.List.of());
    StepVerifier.create(handler.logout(mock(WebFilterExchange.class), authentication))
        .verifyComplete();
  }

  @Test
  void absorbsServiceError() {
    OidcUser user = mock(OidcUser.class);
    when(user.getName()).thenReturn("alice");
    when(service.removeAuthorizedClient(anyString(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("redis down")));
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(user, "n/a", java.util.List.of());

    StepVerifier.create(handler.logout(mock(WebFilterExchange.class), authentication))
        .verifyComplete();
  }
}
