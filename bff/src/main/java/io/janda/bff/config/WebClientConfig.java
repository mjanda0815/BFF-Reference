package io.janda.bff.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Builds the WebClient instances used by the downstream service clients. */
@Configuration
public class WebClientConfig {

  /** WebClient bean qualifier for the user-service. */
  public static final String USER_SERVICE = "userServiceWebClient";

  /** WebClient bean qualifier for the notification-service. */
  public static final String NOTIFICATION_SERVICE = "notificationServiceWebClient";

  /** WebClient bean qualifier for the activity-service. */
  public static final String ACTIVITY_SERVICE = "activityServiceWebClient";

  private final BffProperties properties;

  public WebClientConfig(BffProperties properties) {
    this.properties = properties;
  }

  @Bean(USER_SERVICE)
  WebClient userServiceWebClient() {
    return buildClient(properties.getUserServiceUrl());
  }

  @Bean(NOTIFICATION_SERVICE)
  WebClient notificationServiceWebClient() {
    return buildClient(properties.getNotificationServiceUrl());
  }

  @Bean(ACTIVITY_SERVICE)
  WebClient activityServiceWebClient() {
    return buildClient(properties.getActivityServiceUrl());
  }

  private WebClient buildClient(String baseUrl) {
    Duration timeout = Duration.ofMillis(properties.getServiceTimeoutMillis());
    HttpClient httpClient =
        HttpClient.create()
            .responseTimeout(timeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(
                            new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(
                            new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));
    return WebClient.builder()
        .baseUrl(baseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }
}
