package de.bafa.activityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the activity-service microservice.
 *
 * <p>See {@link de.bafa.userservice.UserServiceApplication} for the role of downstream services
 * in this reference architecture.
 */
@SpringBootApplication
public class ActivityServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ActivityServiceApplication.class, args);
  }
}
