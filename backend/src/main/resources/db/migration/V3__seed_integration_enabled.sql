-- V3: Seed integration provider enabled flags (default false).
INSERT IGNORE INTO app_setting (setting_key, setting_value, secret) VALUES
    ('integration.adzuna.enabled', 'false', FALSE),
    ('integration.jsearch.enabled', 'false', FALSE),
    ('integration.greenhouse.enabled', 'false', FALSE),
    ('integration.lever.enabled', 'false', FALSE),
    ('notification.email.to', '', FALSE),
    ('notification.telegram.bot-token', '', TRUE),
    ('notification.telegram.chat-id', '', FALSE),
    ('integration.adzuna.app-id', '', FALSE),
    ('integration.adzuna.app-key', '', TRUE),
    ('integration.jsearch.api-key', '', TRUE),
    ('integration.greenhouse.boards', '', FALSE),
    ('integration.lever.companies', '', FALSE);
