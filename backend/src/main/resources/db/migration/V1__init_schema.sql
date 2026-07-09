-- ============================================================================
-- V1: Initial schema for Job Search Copilot
-- Engine: InnoDB, charset utf8mb4
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Authentication user (single-user platform, but modelled properly)
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL DEFAULT 'ADMIN',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT uq_app_user_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Profile: the user's job-search preferences
-- ---------------------------------------------------------------------------
CREATE TABLE profile (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    full_name          VARCHAR(255) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    phone              VARCHAR(50),
    resume_path        VARCHAR(1024),
    linked_in          VARCHAR(512),
    github             VARCHAR(512),
    portfolio          VARCHAR(512),
    titles             TEXT,              -- JSON array of desired titles
    locations          TEXT,              -- JSON array of desired locations
    salary_minimum     INT,
    remote_preference  VARCHAR(30)  NOT NULL DEFAULT 'ANY',  -- REMOTE | HYBRID | ONSITE | ANY
    seniority          VARCHAR(30),       -- INTERN | JUNIOR | MID | SENIOR | LEAD | PRINCIPAL
    keywords           TEXT,              -- JSON array of required keywords
    excluded_companies TEXT,              -- JSON array of blacklisted companies
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_profile PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Job source: an external provider configuration
-- ---------------------------------------------------------------------------
CREATE TABLE job_source (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(150) NOT NULL,
    type          VARCHAR(50)  NOT NULL,   -- ADZUNA | JSEARCH | GREENHOUSE | LEVER | ...
    configuration TEXT,                    -- JSON provider-specific config
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_job_source PRIMARY KEY (id),
    CONSTRAINT uq_job_source_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Posting: a normalized job posting
-- ---------------------------------------------------------------------------
CREATE TABLE posting (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    source        VARCHAR(50)  NOT NULL,   -- provider type that produced it
    external_id   VARCHAR(255) NOT NULL,   -- provider-native id used for de-duplication
    title         VARCHAR(512) NOT NULL,
    company       VARCHAR(255),
    location      VARCHAR(255),
    remote        BOOLEAN      NOT NULL DEFAULT FALSE,
    salary        VARCHAR(120),
    salary_min    INT,
    salary_max    INT,
    description   MEDIUMTEXT,
    url           VARCHAR(1024) NOT NULL,
    score         INT          NOT NULL DEFAULT 0,   -- 0-100
    fingerprint   CHAR(64)     NOT NULL,             -- sha-256 of title+company+location
    posted_at     DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_posting PRIMARY KEY (id),
    CONSTRAINT uq_posting_source_external UNIQUE (source, external_id),
    CONSTRAINT uq_posting_fingerprint UNIQUE (fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_posting_score ON posting (score);
CREATE INDEX idx_posting_posted_at ON posting (posted_at);
CREATE INDEX idx_posting_company ON posting (company);

-- ---------------------------------------------------------------------------
-- Application: tracks the user's application lifecycle for a posting
-- ---------------------------------------------------------------------------
CREATE TABLE application (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    posting_id   BIGINT      NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'MATCHED', -- MATCHED|SAVED|APPLIED|VIEWED|INTERVIEW|OFFER|REJECTED
    applied_date DATETIME,
    notes        TEXT,
    method       VARCHAR(50),  -- MANUAL | PREPARED | EMAIL | REFERRAL
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_application PRIMARY KEY (id),
    CONSTRAINT uq_application_posting UNIQUE (posting_id),
    CONSTRAINT fk_application_posting FOREIGN KEY (posting_id)
        REFERENCES posting (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_application_status ON application (status);

-- ---------------------------------------------------------------------------
-- Saved job: quick-save / bookmark independent from the application pipeline
-- ---------------------------------------------------------------------------
CREATE TABLE saved_job (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    posting_id BIGINT   NOT NULL,
    note       TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_saved_job PRIMARY KEY (id),
    CONSTRAINT uq_saved_job_posting UNIQUE (posting_id),
    CONSTRAINT fk_saved_job_posting FOREIGN KEY (posting_id)
        REFERENCES posting (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Dismissed job: hidden postings so they are not resurfaced
-- ---------------------------------------------------------------------------
CREATE TABLE dismissed_job (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    posting_id BIGINT   NOT NULL,
    reason     VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dismissed_job PRIMARY KEY (id),
    CONSTRAINT uq_dismissed_job_posting UNIQUE (posting_id),
    CONSTRAINT fk_dismissed_job_posting FOREIGN KEY (posting_id)
        REFERENCES posting (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Notification: an outbound notification record
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    channel    VARCHAR(30)  NOT NULL,   -- EMAIL | TELEGRAM
    title      VARCHAR(255) NOT NULL,
    message    TEXT         NOT NULL,
    status     VARCHAR(30)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    error      TEXT,
    posting_id BIGINT,
    read_flag  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at    DATETIME,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_posting FOREIGN KEY (posting_id)
        REFERENCES posting (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notification_created_at ON notification (created_at);

-- ---------------------------------------------------------------------------
-- Scheduler log: each background run
-- ---------------------------------------------------------------------------
CREATE TABLE scheduler_log (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    status           VARCHAR(30) NOT NULL,   -- RUNNING | SUCCESS | FAILED
    started_at       DATETIME    NOT NULL,
    finished_at      DATETIME,
    fetched_count    INT         NOT NULL DEFAULT 0,
    new_count        INT         NOT NULL DEFAULT 0,
    duplicate_count  INT         NOT NULL DEFAULT 0,
    notified_count   INT         NOT NULL DEFAULT 0,
    message          TEXT,
    CONSTRAINT pk_scheduler_log PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_scheduler_log_started ON scheduler_log (started_at);

-- ---------------------------------------------------------------------------
-- Application setting: runtime key/value settings (admin panel)
-- ---------------------------------------------------------------------------
CREATE TABLE app_setting (
    setting_key   VARCHAR(120) NOT NULL,
    setting_value TEXT,
    secret        BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_app_setting PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Audit log: security-relevant events
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(100),
    action     VARCHAR(100) NOT NULL,
    detail     TEXT,
    ip_address VARCHAR(64),
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_log_created ON audit_log (created_at);
