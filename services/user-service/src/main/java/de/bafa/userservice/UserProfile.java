package de.bafa.userservice;

/**
 * User profile representation returned by the user-service.
 *
 * <p>The record is intentionally owned by this service, not shared with the BFF — the BFF defines
 * its own {@code UserProfile} in {@code de.bafa.bff.domain.model}. Keeping the schemas separate
 * means the BFF and the downstream can evolve independently; the contract between them lives in
 * their JSON, not in a shared code dependency.
 */
public record UserProfile(String userId, String displayName, String role, String avatarUrl) {}
