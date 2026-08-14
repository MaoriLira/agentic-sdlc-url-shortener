-- Starting value = 62^6, so Base62-encoded output is exactly 7 characters
-- until the counter exceeds 62^7 - 1, at which point it grows to 8 (VARCHAR(20) allows this).
CREATE SEQUENCE core.short_code_seq START WITH 56800235584 INCREMENT BY 1;
