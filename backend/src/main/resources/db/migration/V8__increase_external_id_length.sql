ALTER TABLE posting DROP INDEX uq_posting_source_external;
ALTER TABLE posting MODIFY external_id VARCHAR(1024) NOT NULL;
ALTER TABLE posting ADD CONSTRAINT uq_posting_source_external UNIQUE (source, external_id(255));
