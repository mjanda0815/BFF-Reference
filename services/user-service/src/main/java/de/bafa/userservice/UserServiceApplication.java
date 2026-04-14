package de.bafa.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the user-service microservice.
 *
 * <p><b>Role in the reference architecture:</b> a JWT-secured REST microservice that returns user
 * profile data. It is the canonical example of a <em>downstream</em> service in the BFF pattern —
 * it never talks to the browser directly; only the BFF forwards bearer tokens to it. For a team
 * building their own downstream service this module is meant to be copied and renamed.
 *
 * <p>The business logic is deliberately trivial (a single controller returning synthetic data);
 * what is worth copying is the hardened resource-server security setup in {@link SecurityConfig}.
 */
@SpringBootApplication
public class UserServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserServiceApplication.class, args);
  }
}
