ALTER TABLE posting
    ADD INDEX idx_posting_scheduler_run_score (scheduler_run_id, score DESC);
