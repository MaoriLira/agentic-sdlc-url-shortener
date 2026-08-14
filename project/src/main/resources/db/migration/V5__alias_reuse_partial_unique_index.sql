-- URL-501 (R-3 fix): a table-wide UNIQUE constraint on short_code meant a deleted
-- short_code/custom alias could never be reused, even by its original owner, because the
-- soft-deleted row (status = 'DELETED') still occupied the unique value forever.
--
-- Replace the constraint with a partial unique index scoped to ACTIVE rows only. This also
-- supersedes the plain (non-unique) lookup index from V1 that covered the same
-- (short_code) WHERE status = 'ACTIVE' shape — one index now serves both the uniqueness
-- guarantee and the hot-path lookup performance it was already providing.
ALTER TABLE core.url_mappings DROP CONSTRAINT url_mappings_short_code_key;
DROP INDEX core.idx_url_mappings_short_code_active;

CREATE UNIQUE INDEX idx_url_mappings_short_code_active
    ON core.url_mappings (short_code) WHERE status = 'ACTIVE';
