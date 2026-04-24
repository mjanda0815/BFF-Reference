package de.bafa.userservice;

import jakarta.validation.constraints.NotBlank;

/**
 * Write-step payload for the distributed-write saga demo.
 *
 * <p>The {@code announcementId} is assigned by the BFF orchestrator so every participating
 * service can be compensated with the same id. {@code forceFail} lets the demo reproducibly
 * trigger a failure in this step to exercise compensation.
 */
public record AnnouncementCommand(
    @NotBlank String announcementId, String message, boolean forceFail) {}
