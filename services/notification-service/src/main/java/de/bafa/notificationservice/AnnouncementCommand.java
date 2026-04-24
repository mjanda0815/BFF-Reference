package de.bafa.notificationservice;

import jakarta.validation.constraints.NotBlank;

/**
 * Write-step payload for the distributed-write saga demo.
 *
 * <p>The {@code announcementId} is shared across the three services so the orchestrator can
 * issue a targeted compensation ({@code DELETE /api/notifications/me/announcements/{id}}) if a
 * later step fails. {@code forceFail=true} lets the demo exercise the compensation path.
 */
public record AnnouncementCommand(
    @NotBlank String announcementId, String title, String message, boolean forceFail) {}
