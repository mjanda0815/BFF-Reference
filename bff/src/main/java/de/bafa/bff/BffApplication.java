package de.bafa.bff;

import de.bafa.bff.config.BffProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Entry point for the Backend-for-Frontend application. */
@SpringBootApplication
@EnableConfigurationProperties(BffProperties.class)
public class BffApplication {

  public static void main(String[] args) {
    SpringApplication.run(BffApplication.class, args);
  }
}
