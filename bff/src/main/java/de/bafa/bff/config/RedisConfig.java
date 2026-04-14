package de.bafa.bff.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

/**
 * Enables reactive Redis-backed Spring Sessions.
 *
 * <p><b>Why Redis:</b> the BFF must be horizontally scalable and stateless at the JVM level —
 * sessions live in Redis so any replica can serve any request. Storing access and refresh tokens
 * server-side (inside the session) is also what keeps them out of the browser. See {@code
 * docs/adr/ADR-003-redis-session-store.md}.
 *
 * <p><b>Why the conditional:</b> the flag {@code bff.session.redis-enabled} is turned off in
 * {@code @WebFluxTest} slices so those tests do not need a Redis container. In production and the
 * integration tests the flag is on (the default).
 *
 * <p>The Redis connection itself is auto-configured by Spring Boot from {@code spring.data.redis.*}
 * in {@code application.yml}; this class only flips the Spring Session integration on.
 */
@Configuration
@ConditionalOnProperty(name = "bff.session.redis-enabled", havingValue = "true", matchIfMissing = true)
@EnableRedisWebSession
public class RedisConfig {}
