package de.bafa.bff.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration — only the configured frontend origin is permitted.
 *
 * <p><b>Blueprint note:</b> because the SPA is served from the same origin as the BFF (nginx
 * reverse-proxies both), CORS is effectively bypassed in the default setup. This config exists so
 * that alternative deployments (SPA on a separate origin, local dev with two ports) still work
 * without loosening {@link #corsConfigurationSource() the rules} globally. Do <em>not</em> widen
 * {@code allowedOrigins} to a wildcard — {@code allowCredentials=true} plus {@code "*"} is
 * rejected by browsers, and even if it weren't, it would open the BFF to CSRF from any site.
 *
 * <p>{@code X-XSRF-TOKEN} is both allowed (SPA sends it) and exposed (SPA can read the header the
 * BFF may set on the first response). The rest of the header list is the minimum required for a
 * cookie-authenticated JSON API.
 */
@Configuration
public class CorsConfig {

  private final BffProperties properties;

  public CorsConfig(BffProperties properties) {
    this.properties = properties;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(properties.getFrontendOrigin()));
    config.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "Accept"));
    config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
