ALTER TABLE posting
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE AFTER created_at;

CREATE INDEX idx_posting_archived ON posting (archived);
