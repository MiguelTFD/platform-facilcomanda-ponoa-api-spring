package com.facilcomanda.ponoa.auth.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("refresh_expires_in") Long refreshExpiresIn,
    @JsonProperty("scope") String scope) {}
