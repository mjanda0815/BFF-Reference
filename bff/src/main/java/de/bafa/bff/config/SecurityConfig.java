package de.bafa.bff.config;

import de.bafa.bff.security.SessionInvalidationHandler;
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
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive security configuration — the architectural heart of the BFF.
 *
 * <p>Implements the Backend-for-Frontend pattern with: Redis-backed sessions, Keycloak OIDC login,
 * a Double-Submit-Cookie CSRF strategy and resilient {@code 401} responses for {@code /api/**}.
 *
 * <p><b>Key design choices a team should understand before copying this class:</b>
 *
 * <ul>
 *   <li><b>CSRF via double-submit cookie</b> ({@link CookieServerCsrfTokenRepository}). The
 *       browser receives the token as a readable cookie ({@code XSRF-TOKEN}) and echoes it as a
 *       request header ({@code X-XSRF-TOKEN}). This pairs naturally with a cookie-authenticated
 *       SPA; see {@code docs/security-concept.md} for the threat model.
 *   <li><b>Plain (non-XOR) CSRF token handler.</b> The SPA sends the raw token back unchanged. The
 *       XOR handler is only needed when the token is embedded in HTML bodies (BREACH defence),
 *       which does not apply to a pure JSON API.
 *   <li><b>Smart authentication entry point.</b> Unauthenticated {@code /api/**} requests get a
 *       clean {@code 401} so the SPA's HTTP interceptor can react, while navigation requests get
 *       a {@code 302} redirect into the Keycloak authorization endpoint.
 *   <li><b>NoOp request cache.</b> Spring's default caches the pre-login request to replay after
 *       login — counter-productive for a pure SPA, where we always want to land on the dashboard.
 *   <li><b>RP-initiated logout.</b> Local session + Redis tokens are cleared <em>and</em> the
 *       browser is redirected to Keycloak's {@code end_session_endpoint} so the SSO session ends
 *       too. Without this the user would silently be re-logged-in on the next /login.
 * </ul>
 *
 * <p>The {@code permitAll} list below should be kept minimal and reviewed on every fork — every
 * path added here bypasses authentication.
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
      ServerHttpSecurity http,
      ReactiveClientRegistrationRepository clientRegistrationRepository) {

    // Plain (non-XOR) handler: the SPA reads the raw token from the XSRF-TOKEN cookie and
    // echoes it back as the X-XSRF-TOKEN header. XorServerCsrfTokenRequestAttributeHandler
    // would expect an XOR-masked token in the header and reject raw values with 403. BREACH
    // protection (the reason the XOR handler exists) is only relevant when the token is
    // rendered into an HTML response body, which this pure SPA never does.
    ServerCsrfTokenRequestAttributeHandler csrfHandler =
        new ServerCsrfTokenRequestAttributeHandler();
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

  /**
   * RP-initiated logout: after the local session and Redis state are cleaned up, the browser is
   * redirected to Keycloak's {@code end_session_endpoint} so the SSO session is terminated too.
   * The {@link KeycloakClientRegistrationConfig} wires that endpoint to the public (browser-
   * facing) Keycloak URL so this redirect actually works from outside the docker network.
   */
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
