package io.janda.bff.adapter.web.dto;

/** Minimal user information returned to the SPA (intentionally token-free). */
public record UserInfoResponse(String userId, String displayName, String email) {}
