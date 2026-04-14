package de.bafa.bff.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed configuration properties bound from {@code application.yml} under the prefix
 * {@code bff}.
 *
 * <p><b>Blueprint rationale:</b> all tunables are centralised here with JSR-380 validation. When a
 * team forks this module for their own product they should audit every field below and either set
 * a sensible default for their environment or fail fast at startup via {@code @NotBlank}. The
 * required (un-defaulted) fields are the three downstream service URLs — if you copy this module
 * and do not configure them the context will refuse to start, which is intentional.
 *
 * <p><b>Cookie security note:</b> {@link #cookieSecure} defaults to {@code false} for local dev
 * (HTTP). Production deployments <em>must</em> set {@code bff.cookie-secure=true} so session
 * cookies are only sent over HTTPS.
 */
@Validated
@ConfigurationProperties(prefix = "bff")
public class BffProperties {

  @NotBlank private String frontendOrigin = "http://localhost";

  @Min(60)
  private int sessionTimeoutSeconds = 1800;

  private boolean cookieSecure = false;

  @NotBlank private String userServiceUrl;

  @NotBlank private String notificationServiceUrl;

  @NotBlank private String activityServiceUrl;

  @Min(1000)
  private long serviceTimeoutMillis = 5000;

  public String getFrontendOrigin() {
    return frontendOrigin;
  }

  public void setFrontendOrigin(String frontendOrigin) {
    this.frontendOrigin = frontendOrigin;
  }

  public int getSessionTimeoutSeconds() {
    return sessionTimeoutSeconds;
  }

  public void setSessionTimeoutSeconds(int sessionTimeoutSeconds) {
    this.sessionTimeoutSeconds = sessionTimeoutSeconds;
  }

  public boolean isCookieSecure() {
    return cookieSecure;
  }

  public void setCookieSecure(boolean cookieSecure) {
    this.cookieSecure = cookieSecure;
  }

  public String getUserServiceUrl() {
    return userServiceUrl;
  }

  public void setUserServiceUrl(String userServiceUrl) {
    this.userServiceUrl = userServiceUrl;
  }

  public String getNotificationServiceUrl() {
    return notificationServiceUrl;
  }

  public void setNotificationServiceUrl(String notificationServiceUrl) {
    this.notificationServiceUrl = notificationServiceUrl;
  }

  public String getActivityServiceUrl() {
    return activityServiceUrl;
  }

  public void setActivityServiceUrl(String activityServiceUrl) {
    this.activityServiceUrl = activityServiceUrl;
  }

  public long getServiceTimeoutMillis() {
    return serviceTimeoutMillis;
  }

  public void setServiceTimeoutMillis(long serviceTimeoutMillis) {
    this.serviceTimeoutMillis = serviceTimeoutMillis;
  }
}
