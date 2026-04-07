package io.janda.bff.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Strongly typed configuration properties for the BFF. */
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
