package de.bafa.bff.adapter.web.dto;

/**
 * Minimal user information returned to the SPA — intentionally <em>token-free</em>.
 *
 * <p><b>Why so sparse:</b> the frontend only needs enough to render a user-identifying element in
 * the header. Anything more (roles, claims, email verification flag) belongs behind a dedicated
 * endpoint so this payload stays cacheable and low-sensitivity. Nothing derived from the OAuth2
 * tokens (no {@code sub} outside of {@code userId}, no raw claims, no token values) must ever be
 * exposed here.
 */
public record UserInfoResponse(String userId, String displayName, String email) {}
