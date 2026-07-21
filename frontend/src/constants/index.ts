/** Central place for app-wide constants. */

export const TOKEN_STORAGE_KEY = 'jsc.accessToken';
export const REFRESH_TOKEN_STORAGE_KEY = 'jsc.refreshToken';
export const USER_STORAGE_KEY = 'jsc.user';

export const APPLICATION_STATUSES = [
  'MATCHED',
  'SAVED',
  'APPLIED',
  'VIEWED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'GHOSTED_BY_USER',
  'GHOSTED_BY_COMPANY',
] as const;

export const KANBAN_COLUMNS: { key: (typeof APPLICATION_STATUSES)[number]; label: string }[] = [
  { key: 'MATCHED', label: 'Matched' },
  { key: 'SAVED', label: 'Saved' },
  { key: 'APPLIED', label: 'Applied' },
  { key: 'VIEWED', label: 'Viewed' },
  { key: 'INTERVIEW', label: 'Interview' },
  { key: 'OFFER', label: 'Offer' },
  { key: 'REJECTED', label: 'Rejected' },
  { key: 'GHOSTED_BY_USER', label: 'Ghosted by Me' },
  { key: 'GHOSTED_BY_COMPANY', label: 'Ghosted by Company' },
];

export const REMOTE_PREFERENCES = ['REMOTE', 'HYBRID', 'ONSITE', 'ANY'] as const;
export const SENIORITY_LEVELS = ['INTERN', 'JUNIOR', 'MID', 'SENIOR', 'LEAD', 'PRINCIPAL'] as const;
