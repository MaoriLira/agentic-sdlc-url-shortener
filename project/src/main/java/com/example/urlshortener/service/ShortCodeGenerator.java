package com.example.urlshortener.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Generates short codes from a monotonically increasing DB sequence (core.short_code_seq)
 * encoded as Base62. Uniqueness is guaranteed by construction (no collision-retry needed
 * for system-generated codes) — see ADR #1/#2 in the Phase 2 design.
 */
@Component
public class ShortCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public ShortCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextCode() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('core.short_code_seq')", Long.class);
        return Base62Encoder.encode(next);
    }
}
