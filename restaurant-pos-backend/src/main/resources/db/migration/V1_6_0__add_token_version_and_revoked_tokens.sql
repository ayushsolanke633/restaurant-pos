-- Migration V1_6_0: Add token_version to users and create revoked_tokens table for persistent JWT revocation
-- AnnaDhara P0-3 Stage 3B

ALTER TABLE users
ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS revoked_tokens (
    jti VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    restaurant_id BIGINT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at ON revoked_tokens (expires_at);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_user_id ON revoked_tokens (user_id);
