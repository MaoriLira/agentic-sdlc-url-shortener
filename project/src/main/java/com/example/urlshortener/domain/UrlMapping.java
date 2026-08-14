package com.example.urlshortener.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * URL-701: {@code id} and {@code createdAt} are deliberately read-only (field-level
 * {@code @Setter} only where a setter existed before Lombok) — {@code id} is DB-generated,
 * {@code createdAt} is stamped by {@link #onCreate()}. {@code @Builder}/
 * {@code @AllArgsConstructor} do let a caller pass both explicitly (a minor, deliberate
 * widening over the pre-Lombok API — see ADR-14) but nothing in application code does.
 */
@Entity
@Table(name = "url_mappings", schema = "core")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Uniqueness is enforced by a partial DB index scoped to status = 'ACTIVE' (V5 migration),
    // not a plain column constraint — a deleted short_code/alias can be reused. JPA can't
    // express a conditional unique constraint declaratively, so no `unique = true` here.
    @Setter
    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Setter
    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Setter
    @Column(name = "owner_client_id")
    private Long ownerClientId;

    @Setter
    @Column(name = "is_custom_alias", nullable = false)
    private boolean customAlias;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UrlStatus status = UrlStatus.ACTIVE;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
