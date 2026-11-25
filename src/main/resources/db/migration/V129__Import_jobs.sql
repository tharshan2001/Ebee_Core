-- History of CSV / Excel imports (products, opening stock, customers, suppliers).
CREATE TABLE import_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_type   VARCHAR(30)  NOT NULL,
    file_name     VARCHAR(255),
    dry_run       BOOLEAN      NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('VALID', 'INVALID', 'APPLIED', 'FAILED')),
    total_rows    INTEGER      NOT NULL DEFAULT 0,
    created_count INTEGER      NOT NULL DEFAULT 0,
    updated_count INTEGER      NOT NULL DEFAULT 0,
    skipped_count INTEGER      NOT NULL DEFAULT 0,
    error_count   INTEGER      NOT NULL DEFAULT 0,
    -- First errors as JSON: [{"row": 5, "column": "price", "message": "..."}]
    errors        TEXT,
    created_by    UUID,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_import_jobs_created ON import_jobs (created_at DESC);
