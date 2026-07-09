-- ============================================================================
-- V2: Seed default runtime settings and job sources.
-- Values are non-secret defaults; secrets remain blank and are injected at runtime.
-- ============================================================================

INSERT INTO app_setting (setting_key, setting_value, secret) VALUES
    ('scheduler.cron', '0 */30 * * * *', FALSE),
    ('scheduler.enabled', 'true', FALSE),
    ('notification.score-threshold', '70', FALSE),
    ('notification.email.enabled', 'false', FALSE),
    ('notification.telegram.enabled', 'false', FALSE),
    ('automation.browser-path', '', FALSE),
    ('automation.browser-profile-path', '', FALSE),
    ('profile.resume-path', '', FALSE);

INSERT INTO job_source (name, type, configuration, enabled) VALUES
    ('Adzuna',     'ADZUNA',     '{"country":"us","resultsPerPage":50}', FALSE),
    ('JSearch',    'JSEARCH',    '{"pages":1,"numPages":1}',             FALSE),
    ('Greenhouse', 'GREENHOUSE', '{"boards":[]}',                        FALSE),
    ('Lever',      'LEVER',      '{"companies":[]}',                     FALSE);
