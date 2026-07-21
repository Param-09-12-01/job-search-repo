ALTER TABLE posting
    ADD COLUMN scheduler_run_id BIGINT NULL AFTER archived,
    ADD INDEX idx_posting_scheduler_run (scheduler_run_id);
