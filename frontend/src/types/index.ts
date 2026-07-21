/** Types mirroring the backend DTOs. */

export type RemotePreference = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'ANY';
export type Seniority = 'INTERN' | 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD' | 'PRINCIPAL';
export type ApplicationStatus =
  | 'MATCHED'
  | 'SAVED'
  | 'APPLIED'
  | 'VIEWED'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED';
export type ApplicationMethod = 'MANUAL' | 'PREPARED' | 'EMAIL' | 'REFERRAL';

export interface ManualApplicationRequest {
  title: string;
  company?: string;
  location?: string;
  url?: string;
  description?: string;
  salary?: string;
  notes?: string;
  status?: ApplicationStatus;
  method?: ApplicationMethod;
}
export type NotificationChannel = 'EMAIL' | 'TELEGRAM';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';
export type SchedulerRunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  username: string;
  role: string;
}

export interface Profile {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  resumePath?: string;
  linkedIn?: string;
  github?: string;
  portfolio?: string;
  titles: string[];
  locations: string[];
  salaryMinimum?: number;
  remotePreference: RemotePreference;
  seniority?: Seniority;
  keywords: string[];
  excludedCompanies: string[];
  createdAt: string;
  updatedAt: string;
}

export type ProfileRequest = Omit<Profile, 'id' | 'createdAt' | 'updatedAt'>;

export interface Posting {
  id: number;
  source: string;
  title: string;
  company?: string;
  location?: string;
  remote: boolean;
  salary?: string;
  salaryMin?: number;
  salaryMax?: number;
  description?: string;
  url: string;
  score: number;
  postedAt?: string;
  createdAt: string;
  saved: boolean;
  dismissed: boolean;
  applied: boolean;
}

export interface Application {
  id: number;
  posting: Posting;
  status: ApplicationStatus;
  appliedDate?: string;
  notes?: string;
  method?: ApplicationMethod;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationItem {
  id: number;
  channel: NotificationChannel;
  title: string;
  message: string;
  status: NotificationStatus;
  error?: string;
  postingId?: number;
  read: boolean;
  createdAt: string;
  sentAt?: string;
}

export interface SchedulerLog {
  id: number;
  status: SchedulerRunStatus;
  startedAt: string;
  finishedAt?: string;
  fetchedCount: number;
  newCount: number;
  duplicateCount: number;
  notifiedCount: number;
  message?: string;
}

export interface DashboardStats {
  newJobs: number;
  savedJobs: number;
  appliedJobs: number;
  interview: number;
  rejected: number;
  offer: number;
  totalPostings: number;
}

export interface Dashboard {
  stats: DashboardStats;
  recentNotifications: NotificationItem[];
  recentSchedulerRuns: SchedulerLog[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CursorPageResponse<T> {
  content: T[];
  nextCursor?: string;
  hasMore: boolean;
  size: number;
}

export interface Settings {
  values: Record<string, string>;
}

export interface PrepareApplicationResult {
  launched: boolean;
  submitted: boolean;
  message: string;
  postingUrl: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
