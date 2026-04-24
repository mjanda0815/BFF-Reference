package de.bafa.userservice;

import java.time.Instant;

/**
 * User-local record that a subscription to an announcement has been persisted.
 *
 * <p>Returned to the BFF by the announcement POST endpoint and re-used as the value in the
 * idempotent {@link AnnouncementSubscriptionStore}. The {@code announcementId} is shared with
 * the other saga participants (notification, activity), so all three services can be
 * compensated with the same key.
 *
 * <p><b>Für die Übernahme in ein neues Produkt:</b> Dieses Record ist exemplarisch — für echte
 * Fachlichkeit ersetzt das Produkt-Team es durch eine JPA-Entity (mit Flyway-Migration) oder
 * ein anderes persistentes Modell. Die {@code announcementId}-Feld-Rolle („geteilte
 * Saga-ID") bleibt dabei erhalten, damit die Kompensation weiterhin funktioniert.
 */
public record AnnouncementSubscription(
    String announcementId, String userId, String message, Instant createdAt) {}
