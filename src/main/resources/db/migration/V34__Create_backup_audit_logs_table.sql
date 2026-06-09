CREATE TABLE backup_audit_logs (
    id UUID PRIMARY KEY,
    action VARCHAR(20) NOT NULL,
    filename VARCHAR(255),
    detail TEXT,
    performed_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_backup_audit_created_at ON backup_audit_logs(created_at DESC);
