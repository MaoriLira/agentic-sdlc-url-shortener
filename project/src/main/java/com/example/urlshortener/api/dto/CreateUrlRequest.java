package com.example.urlshortener.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(
        @NotBlank @Size(max = 2048) String longUrl,
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$", message = "customAlias must be 3-20 chars of letters, digits, '-' or '_'")
        String customAlias,
        Instant expiresAt
) {
}
