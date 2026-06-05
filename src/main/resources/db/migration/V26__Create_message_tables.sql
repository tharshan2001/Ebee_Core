CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sent_by UUID NOT NULL REFERENCES users(id),
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    total_recipients INT NOT NULL DEFAULT 0,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message_recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    recipient_type VARCHAR(10) NOT NULL,
    recipient_id UUID NOT NULL,
    name VARCHAR(255),
    phone_number VARCHAR(20),
    email VARCHAR(255),
    sent_sms BOOLEAN NOT NULL DEFAULT FALSE,
    sent_email BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_messages_sent_by ON messages(sent_by, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_recipients_msg ON message_recipients(message_id);
