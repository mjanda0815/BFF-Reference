package com.example.bff.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SessionTokenServiceTest {

  private ServerOAuth2AuthorizedClientRepository repository;
  private ReactiveOAuth2AuthorizedClientService authorizedClientService;
  private SessionTokenService service;

  @BeforeEach
  void setUp() {
    repository = mock(ServerOAuth2AuthorizedClientRepository.class);
    authorizedClientService = mock(ReactiveOAuth2AuthorizedClientService.class);
    service = new SessionTokenService(repository, authorizedClientService);
  }

  private OAuth2AuthorizedClient sampleClient(String tokenValue) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("keycloak")
            .clientId("bff-client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback")
            .authorizationUri("http://kc/auth")
            .tokenUri("http://kc/token")
            .build();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, tokenValue, Instant.now(), Instant.now().plusSeconds(300));
    return new OAuth2AuthorizedClient(registration, "alice", accessToken);
  }

  @Test
  void loadsAuthorizedClient() {
    OidcUser user = mock(OidcUser.class);
    when(user.getName()).thenReturn("alice");
    OAuth2AuthorizedClient client = sampleClient("tok-1");
    when(repository.loadAuthorizedClient(anyString(), any(), any())).thenReturn(Mono.just(client));

    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

    StepVerifier.create(service.loadAuthorizedClient(user, exchange))
        .assertNext(loaded -> assertEquals("tok-1", loaded.getAccessToken().getTokenValue()))
        .verifyComplete();
  }

  @Test
  void returnsCurrentAccessToken() {
    OidcUser user = mock(OidcUser.class);
    when(user.getName()).thenReturn("alice");
    when(repository.loadAuthorizedClient(anyString(), any(), any()))
        .thenReturn(Mono.just(sampleClient("tok-2")));

    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

    StepVerifier.create(service.currentAccessToken(user, exchange))
        .expectNext("tok-2")
        .verifyComplete();
  }

  @Test
  void invalidateRemovesAuthorizedClient() {
    OidcUser user = mock(OidcUser.class);
    when(user.getName()).thenReturn("alice");
    when(authorizedClientService.removeAuthorizedClient(anyString(), anyString()))
        .thenReturn(Mono.empty());
    when(repository.removeAuthorizedClient(anyString(), any(), any())).thenReturn(Mono.empty());

    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

    StepVerifier.create(service.invalidate(user, exchange)).verifyComplete();
  }

  @Test
  void currentAccessTokenIsEmptyWhenNoClient() {
    OidcUser user = mock(OidcUser.class);
    when(user.getName()).thenReturn("alice");
    when(repository.loadAuthorizedClient(anyString(), any(), any())).thenReturn(Mono.empty());

    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));

    StepVerifier.create(service.currentAccessToken(user, exchange)).verifyComplete();
  }
}
