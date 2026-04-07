package io.janda.bff.config;

import io.janda.bff.security.SessionInvalidationHandler;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive security configuration for the BFF.
 *
 * <p>Implements the Backend-for-Frontend pattern with: Redis-backed sessions, Keycloak OIDC login,
 * a Double-Submit-Cookie CSRF strategy and resilient {@code 401} responses for {@code /api/**}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private static final String FRONTEND_DASHBOARD_PATH = "/";

  private final BffProperties properties;
  private final CorsConfigurationSource corsConfigurationSource;
  private final SessionInvalidationHandler sessionInvalidationHandler;

  public SecurityConfig(
      BffProperties properties,
      CorsConfigurationSource corsConfigurationSource,
      SessionInvalidationHandler sessionInvalidationHandler) {
    this.properties = properties;
    this.corsConfigurationSource = corsConfigurationSource;
    this.sessionInvalidationHandler = sessionInvalidationHandler;
  }

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(
      ServerHttpSecurity http, ReactiveClientRegistrationRepository clientRegistrationRepository) {

    XorServerCsrfTokenRequestAttributeHandler csrfHandler =
        new XorServerCsrfTokenRequestAttributeHandler();
    csrfHandler.setTokenFromMultipartDataEnabled(false);

    CookieServerCsrfTokenRepository csrfTokenRepository =
        CookieServerCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookieCustomizer(
        cookie ->
            cookie
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .httpOnly(false));

    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(csrfHandler))
        .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
        .authorizeExchange(
            authz ->
                authz
                    .pathMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/login",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/logout/backchannel")
                    .permitAll()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(this::handleAuthenticationEntryPoint)
                    .accessDeniedHandler(
                        new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN)))
        .oauth2Login(
            oauth ->
                oauth.authenticationSuccessHandler(
                    new RedirectServerAuthenticationSuccessHandler(
                        properties.getFrontendOrigin() + FRONTEND_DASHBOARD_PATH)))
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutHandler(combinedLogoutHandler())
                    .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
        .addFilterAfter(csrfCookieEnsuringFilter(), SecurityWebFiltersOrder.CSRF);

    return http.build();
  }

  private Mono<Void> handleAuthenticationEntryPoint(
      ServerWebExchange exchange,
      org.springframework.security.core.AuthenticationException denied) {
    if (isApiRequest(exchange)) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
    exchange
        .getResponse()
        .getHeaders()
        .setLocation(URI.create("/oauth2/authorization/keycloak"));
    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
    return exchange.getResponse().setComplete();
  }

  private ServerLogoutHandler combinedLogoutHandler() {
    return new DelegatingServerLogoutHandler(
        new SecurityContextServerLogoutHandler(),
        new WebSessionServerLogoutHandler(),
        sessionInvalidationHandler);
  }

  private ServerLogoutSuccessHandler oidcLogoutSuccessHandler(
      ReactiveClientRegistrationRepository clientRegistrationRepository) {
    OidcClientInitiatedServerLogoutSuccessHandler handler =
        new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri(properties.getFrontendOrigin() + FRONTEND_DASHBOARD_PATH);
    return handler;
  }

  /**
   * Ensures the CSRF token cookie is materialised on every request, so the SPA can read it from
   * the {@code XSRF-TOKEN} cookie and echo it as a {@code X-XSRF-TOKEN} header.
   */
  @Bean
  WebFilter csrfCookieEnsuringFilter() {
    return (ServerWebExchange exchange, WebFilterChain chain) -> {
      Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
      if (csrfToken == null) {
        return chain.filter(exchange);
      }
      return csrfToken.doOnSuccess(token -> {}).then(chain.filter(exchange));
    };
  }

  private boolean isApiRequest(ServerWebExchange exchange) {
    return exchange.getRequest().getPath().value().startsWith("/api/");
  }
}
