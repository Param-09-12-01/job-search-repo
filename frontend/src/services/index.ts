import { api } from './api';
import type {
  Application,
  ApplicationStatus,
  CursorPageResponse,
  Dashboard,
  GmailSyncResponse,
  GmailSyncStatusResponse,
  ManualApplicationRequest,
  NotificationItem,
  PageResponse,
  Posting,
  PrepareApplicationResult,
  Profile,
  ProfileRequest,
  SchedulerLog,
  Settings,
} from '@/types';

// --- Auth -----------------------------------------------------------------------------------
export const authService = {
  login: (username: string, password: string) =>
    api.post('/auth/login', { username, password }).then((r) => r.data),
};

// --- Dashboard ------------------------------------------------------------------------------
export const dashboardService = {
  get: () => api.get<Dashboard>('/dashboard').then((r) => r.data),
};

// --- Profile --------------------------------------------------------------------------------
export const profileService = {
  get: () => api.get<Profile>('/profile').then((r) => r.data),
  save: (payload: ProfileRequest) => api.put<Profile>('/profile', payload).then((r) => r.data),
};

// --- Jobs -----------------------------------------------------------------------------------
export interface JobQuery {
  query?: string;
  minScore?: number;
  remote?: boolean;
  source?: string;
  company?: string;
  includeDismissed?: boolean;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

export const jobService = {
  list: (params: JobQuery) =>
    api.get<PageResponse<Posting>>('/jobs', { params }).then((r) => r.data),
  get: (id: number) => api.get<Posting>(`/jobs/${id}`).then((r) => r.data),
  save: (id: number, note?: string) =>
    api.post<Posting>(`/jobs/${id}/save`, { note }).then((r) => r.data),
  unsave: (id: number) => api.delete(`/jobs/${id}/save`).then((r) => r.data),
  dismiss: (id: number, reason?: string) =>
    api.post<Posting>(`/jobs/${id}/dismiss`, { reason }).then((r) => r.data),
  prepare: (id: number) =>
    api.post<PrepareApplicationResult>(`/jobs/${id}/prepare`).then((r) => r.data),
};

// --- Applications ---------------------------------------------------------------------------
export const applicationService = {
  list: () => api.get<Application[]>('/applications').then((r) => r.data),
  board: () =>
    api
      .get<Record<ApplicationStatus, Application[]>>('/applications/board')
      .then((r) => r.data),
  create: (postingId: number, notes?: string) =>
    api.post<Application>('/applications', { postingId, notes }).then((r) => r.data),
  createManual: (req: ManualApplicationRequest) =>
    api.post<Application>('/applications/manual', req).then((r) => r.data),
  update: (id: number, status: ApplicationStatus, notes?: string) =>
    api.patch<Application>(`/applications/${id}`, { status, notes }).then((r) => r.data),
  remove: (id: number) => api.delete(`/applications/${id}`).then((r) => r.data),
};

// --- Notifications --------------------------------------------------------------------------
export const notificationService = {
  list: (page = 0, size = 20) =>
    api
      .get<PageResponse<NotificationItem>>('/notifications', { params: { page, size } })
      .then((r) => r.data),
  unreadCount: () =>
    api.get<{ count: number }>('/notifications/unread-count').then((r) => r.data.count),
  markRead: (id: number) =>
    api.patch<NotificationItem>(`/notifications/${id}/read`).then((r) => r.data),
};

// --- Scheduler ------------------------------------------------------------------------------
export const schedulerService = {
  logs: (page = 0, size = 20) =>
    api
      .get<PageResponse<SchedulerLog>>('/scheduler/logs', { params: { page, size } })
      .then((r) => r.data),
  runNow: () => api.post<SchedulerLog>('/scheduler/run').then((r) => r.data),
  jobsForRun: (runId: number, cursor?: string, size = 20) =>
    api.get<CursorPageResponse<Posting>>('/scheduler/logs/' + runId + '/jobs', { params: { cursor, size } }).then((r) => r.data),
};

// --- Settings -------------------------------------------------------------------------------
export const settingsService = {
  get: () => api.get<Settings>('/settings').then((r) => r.data),
  update: (values: Record<string, string>) =>
    api.put<Settings>('/settings', { values }).then((r) => r.data),
};

export const syncService = {
  triggerGmailSync: (hours?: number) =>
    api.post<GmailSyncResponse>('/sync/gmail', null, { params: hours ? { hours } : {} }).then((r) => r.data),
  getGmailSyncStatus: () =>
    api.get<GmailSyncStatusResponse>('/sync/gmail/status').then((r) => r.data),
  getAuthUrl: () =>
    api.get<{ authUrl: string }>('/sync/gmail/auth-url').then((r) => r.data),
  authCallback: (code: string) =>
    api.post<{ message: string }>('/sync/gmail/auth-callback', null, { params: { code } }).then((r) => r.data),
  disconnectGmail: () =>
    api.post<{ message: string }>('/sync/gmail/disconnect').then((r) => r.data),
  getGmailProfile: () =>
    api.get<any>('/sync/gmail/profile').then((r) => r.data),
  diagnosticGmail: () =>
    api.get<any>('/sync/gmail/diagnostic').then((r) => r.data),
};
