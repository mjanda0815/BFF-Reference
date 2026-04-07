package com.example.bff.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

/** Enables reactive Redis-backed Spring Sessions. */
@Configuration
@ConditionalOnProperty(name = "bff.session.redis-enabled", havingValue = "true", matchIfMissing = true)
@EnableRedisWebSession
public class RedisConfig {}
