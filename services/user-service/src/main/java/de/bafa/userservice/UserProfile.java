package de.bafa.userservice;

/** User profile representation returned by the user-service. */
public record UserProfile(String userId, String displayName, String role, String avatarUrl) {}
