package de.bafa.activityservice;

import jakarta.validation.constraints.NotBlank;

/**
 * Write-step payload for the distributed-write saga demo.
 *
 * <p>The {@code announcementId} is shared across services for compensation. {@code forceFail}
 * triggers a reproducible 500 to exercise the compensation path.
 */
public record AnnouncementCommand(
    @NotBlank String announcementId, String message, boolean forceFail) {}
