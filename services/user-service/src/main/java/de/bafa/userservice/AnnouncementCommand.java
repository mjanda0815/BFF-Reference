package de.bafa.userservice;

import jakarta.validation.constraints.NotBlank;

/**
 * Write-step payload for the distributed-write saga demo.
 *
 * <p>The {@code announcementId} is assigned by the BFF orchestrator so every participating
 * service can be compensated with the same id. {@code forceFail} lets the demo reproducibly
 * trigger a failure in this step to exercise compensation.
 *
 * <p><b>Blueprint-Hinweis:</b> Das {@code @NotBlank} auf der {@code announcementId} ist wichtig
 * — ein leerer Key würde die Idempotenz-Garantie des Stores aushebeln, weil
 * {@link java.util.concurrent.ConcurrentHashMap#putIfAbsent} dann zwei Einträge mit dem
 * Null-/Leerkey nicht unterscheiden könnte. Für eigene Saga-Commands dieselbe
 * Validation-Constraint übernehmen.
 */
public record AnnouncementCommand(
    @NotBlank String announcementId, String message, boolean forceFail) {}
