-- Add password change tracking fields to users
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMP;

-- Create index for performance
CREATE INDEX idx_users_must_change_password ON users(must_change_password);