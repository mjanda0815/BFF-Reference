package de.bafa.notificationservice;

import jakarta.validation.constraints.NotBlank;

/**
 * Write-step payload for the distributed-write saga demo.
 *
 * <p>The {@code announcementId} is shared across the three services so the orchestrator can
 * issue a targeted compensation ({@code DELETE /api/notifications/me/announcements/{id}}) if a
 * later step fails. {@code forceFail=true} lets the demo exercise the compensation path.
 *
 * <p><b>Blueprint-Hinweis:</b> Das {@code @NotBlank} ist nicht kosmetisch — ohne diesen Check
 * könnte ein leerer Key die Idempotenz des Stores untergraben. Für eigene Saga-Commands in
 * anderen Services dieselbe Constraint übernehmen; nur die Fachfelder ({@code title},
 * {@code message} hier) werden pro Produkt angepasst.
 */
public record AnnouncementCommand(
    @NotBlank String announcementId, String title, String message, boolean forceFail) {}
