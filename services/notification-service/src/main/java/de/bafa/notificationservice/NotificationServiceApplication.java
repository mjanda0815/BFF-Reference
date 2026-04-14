package de.bafa.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the notification-service microservice.
 *
 * <p>See {@link de.bafa.userservice.UserServiceApplication} for the role of downstream services
 * in this reference architecture.
 */
@SpringBootApplication
public class NotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
