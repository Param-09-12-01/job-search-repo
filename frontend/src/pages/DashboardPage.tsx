import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  Briefcase,
  Bookmark,
  Send,
  Users,
  XCircle,
  Trophy,
  Database,
  RefreshCw,
  Mail,
  ExternalLink,
  Clock,
  LogOut,
  Bug,
} from 'lucide-react';
import {
  dashboardService,
  syncService,
} from '@/services';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { Input } from '@/components/ui/Input';
import { cn } from '@/utils/cn';
import { useToast } from '@/contexts/ToastContext';
import { extractErrorMessage } from '@/services/api';
import { useState } from 'react';
import { Select } from '@/components/ui/Select';
import type { DashboardStats } from '@/types';

const statCards: {
  key: keyof DashboardStats;
  label: string;
  icon: typeof Briefcase;
  color: string;
}[] = [
  { key: 'newJobs', label: 'New Jobs', icon: Briefcase, color: 'text-blue-500' },
  { key: 'savedJobs', label: 'Saved Jobs', icon: Bookmark, color: 'text-purple-500' },
  { key: 'appliedJobs', label: 'Applied', icon: Send, color: 'text-cyan-500' },
  { key: 'interview', label: 'Interview', icon: Users, color: 'text-amber-500' },
  { key: 'rejected', label: 'Rejected', icon: XCircle, color: 'text-red-500' },
  { key: 'offer', label: 'Offer', icon: Trophy, color: 'text-green-500' },
  { key: 'totalPostings', label: 'Total Postings', icon: Database, color: 'text-slate-400' },
];

export function DashboardPage() {
  const qc = useQueryClient();
  const { success, error } = useToast();
  const [authCode, setAuthCode] = useState('');
  const [showAuthInput, setShowAuthInput] = useState(false);
  const [syncHours, setSyncHours] = useState('');
  const [customHours, setCustomHours] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardService.get,
    refetchInterval: 60_000,
  });

  const { data: syncStatus } = useQuery({
    queryKey: ['sync', 'gmail', 'status'],
    queryFn: syncService.getGmailSyncStatus,
    refetchInterval: 30_000,
  });

  const { data: gmailProfile } = useQuery({
    queryKey: ['sync', 'gmail', 'profile'],
    queryFn: syncService.getGmailProfile,
    enabled: syncStatus?.authorized ?? false,
    retry: false,
  });

  const [showDiagnostic, setShowDiagnostic] = useState(false);
  const { data: gmailDiagnostic, refetch: runDiagnostic, isFetching: diagnosticLoading } = useQuery({
    queryKey: ['sync', 'gmail', 'diagnostic'],
    queryFn: syncService.diagnosticGmail,
    enabled: false,
    retry: false,
  });

  const syncMutation = useMutation({
    mutationFn: () => {
      const hours = syncHours === 'custom'
        ? (parseInt(customHours) || 1)
        : syncHours
          ? parseInt(syncHours)
          : undefined;
      return syncService.triggerGmailSync(hours);
    },
    onSuccess: (res) => {
      success(`Gmail sync complete: ${res.added} added, ${res.skipped} skipped`);
      qc.invalidateQueries({ queryKey: ['sync', 'gmail', 'status'] });
      qc.invalidateQueries({ queryKey: ['applications'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => error('Gmail sync failed', extractErrorMessage(e)),
  });

  const authCallbackMutation = useMutation({
    mutationFn: (code: string) => syncService.authCallback(code),
    onSuccess: (res) => {
      success(res.message);
      setShowAuthInput(false);
      setAuthCode('');
      qc.invalidateQueries({ queryKey: ['sync', 'gmail', 'status'] });
    },
    onError: (e) => error('Auth failed', extractErrorMessage(e)),
  });

  const disconnectMutation = useMutation({
    mutationFn: syncService.disconnectGmail,
    onSuccess: (res) => {
      success(res.message);
      qc.invalidateQueries({ queryKey: ['sync', 'gmail', 'status'] });
    },
    onError: (e) => error('Disconnect failed', extractErrorMessage(e)),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">Your job-search activity at a glance</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7">
        {isLoading
          ? Array.from({ length: 7 }).map((_, i) => <Skeleton key={i} className="h-28" />)
          : statCards.map((card, i) => {
              const value = data?.stats?.[card.key] ?? 0;
              return (
                <motion.div
                  key={card.key}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04 }}
                >
                  <Card>
                    <CardContent className="flex flex-col gap-2 p-4">
                      <card.icon className={cn('h-5 w-5', card.color)} />
                      <span className="text-2xl font-bold">{value}</span>
                      <span className="text-xs text-muted-foreground">{card.label}</span>
                    </CardContent>
                  </Card>
                </motion.div>
              );
            })}
      </div>

      {/* Gmail Sync */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base">
            <Mail className="h-4 w-4 inline mr-2" />
            Gmail Integration
          </CardTitle>
          <div className="flex items-center gap-2">
            {syncStatus?.authorized ? (
              <>
                <Badge variant="success">Connected</Badge>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => disconnectMutation.mutate()}
                  disabled={disconnectMutation.isPending}
                >
                  <LogOut className="h-4 w-4 mr-1" />
                  Disconnect
                </Button>
              </>
            ) : (
              <Badge variant="secondary">Not connected</Badge>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {syncStatus?.authorized ? (
            <div className="space-y-3 text-sm text-muted-foreground">
              {gmailProfile?.emailAddress && (
                <p className="text-xs font-medium text-foreground">
                  Connected as: {gmailProfile.emailAddress}
                  {gmailProfile.messagesTotal !== undefined && (
                    <span className="text-muted-foreground font-normal">
                      {' · '}{gmailProfile.messagesTotal} messages · {gmailProfile.threadsTotal} threads
                    </span>
                  )}
                </p>
              )}
              <div className="flex flex-wrap items-center gap-2">
                <Clock className="h-4 w-4" />
                <Select
                  value={syncHours}
                  onChange={(e) => setSyncHours(e.target.value)}
                  className="w-40"
                >
                  <option value="">Since last sync</option>
                  <option value="1">Last hour</option>
                  <option value="6">Last 6 hours</option>
                  <option value="24">Last 24 hours</option>
                  <option value="72">Last 3 days</option>
                  <option value="168">Last 7 days</option>
                  <option value="custom">Custom...</option>
                </Select>
                {syncHours === 'custom' && (
                  <div className="flex items-center gap-1">
                    <Input
                      type="number"
                      min="1"
                      placeholder="Hours"
                      className="w-20 h-8"
                      value={customHours}
                      onChange={(e) => setCustomHours(e.target.value)}
                    />
                    <span className="text-xs">hrs</span>
                  </div>
                )}
                <Button
                  size="sm"
                  onClick={() => syncMutation.mutate()}
                  disabled={syncMutation.isPending || syncStatus?.isRunning || (syncHours === 'custom' && !customHours)}
                >
                  <RefreshCw className={cn('h-4 w-4 mr-1', (syncMutation.isPending || syncStatus?.isRunning) && 'animate-spin')} />
                  {syncMutation.isPending || syncStatus?.isRunning ? 'Syncing...' : 'Sync Gmail'}
                </Button>
              </div>
              <p>
                Last sync: {syncStatus.lastSyncTime
                  ? new Date(syncStatus.lastSyncTime).toLocaleString()
                  : 'Never'}
              </p>
              {syncStatus.lastSyncResult && (
                <p>
                  Scanned {syncStatus.lastSyncResult.scanned} emails
                  · Detected {syncStatus.lastSyncResult.detected} applications
                  · Added {syncStatus.lastSyncResult.added} new
                  · Skipped {syncStatus.lastSyncResult.skipped}
                  {syncStatus.lastSyncResult.errors > 0 && ` · ${syncStatus.lastSyncResult.errors} errors`}
                </p>
              )}
              <div className="pt-2">
                <Button
                  variant="link"
                  size="sm"
                  className="h-auto p-0 text-xs"
                  onClick={async () => {
                    const res = await syncService.getAuthUrl();
                    window.open(res.authUrl, '_blank');
                    setShowAuthInput(true);
                  }}
                >
                  Re-authorize with a different account
                </Button>
              </div>
              <div className="pt-1">
                <Button
                  variant="link"
                  size="sm"
                  className="h-auto p-0 text-xs text-muted-foreground"
                  onClick={() => { setShowDiagnostic(!showDiagnostic); if (!showDiagnostic) runDiagnostic(); }}
                >
                  <Bug className="h-3 w-3 mr-1" />
                  {showDiagnostic ? 'Hide debug' : 'Debug'}
                </Button>
                {showDiagnostic && (
                  <pre className="mt-2 p-2 bg-muted rounded text-[10px] overflow-auto max-h-40">
                    {diagnosticLoading ? 'Loading...' : JSON.stringify(gmailDiagnostic, null, 2)}
                  </pre>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                Connect your Gmail to automatically detect job applications from sent emails using a local AI model.
              </p>
              {showAuthInput ? (
                <div className="flex gap-2">
                  <Input
                    placeholder="Paste authorization code"
                    value={authCode}
                    onChange={(e) => setAuthCode(e.target.value)}
                  />
                  <Button
                    size="sm"
                    onClick={() => authCallbackMutation.mutate(authCode)}
                    disabled={authCallbackMutation.isPending || !authCode.trim()}
                  >
                    {authCallbackMutation.isPending ? 'Verifying...' : 'Submit'}
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setShowAuthInput(false)}>
                    Cancel
                  </Button>
                </div>
              ) : (
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={async () => {
                      const res = await syncService.getAuthUrl();
                      window.open(res.authUrl, '_blank');
                      setShowAuthInput(true);
                    }}
                  >
                    <ExternalLink className="h-4 w-4 mr-1" />
                    Authorize Gmail
                  </Button>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Recent notifications */}
        <Card>
          <CardHeader>
            <CardTitle>Recent Notifications</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {isLoading ? (
              Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-14" />)
            ) : data?.recentNotifications.length ? (
              data.recentNotifications.map((n) => (
                <div key={n.id} className="flex items-start justify-between gap-3 border-b pb-2 last:border-0">
                  <div>
                    <p className="text-sm font-medium">{n.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {new Date(n.createdAt).toLocaleString()}
                    </p>
                  </div>
                  <Badge variant={n.status === 'SENT' ? 'success' : n.status === 'FAILED' ? 'destructive' : 'secondary'}>
                    {n.channel}
                  </Badge>
                </div>
              ))
            ) : (
              <p className="text-sm text-muted-foreground">No notifications yet.</p>
            )}
          </CardContent>
        </Card>

        {/* Recent scheduler runs */}
        <Card>
          <CardHeader>
            <CardTitle>Recent Scheduler Runs</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {isLoading ? (
              Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-14" />)
            ) : data?.recentSchedulerRuns.length ? (
              data.recentSchedulerRuns.map((run) => (
                <div key={run.id} className="flex items-center justify-between gap-3 border-b pb-2 last:border-0">
                  <div>
                    <p className="text-sm font-medium">
                      {run.newCount} new · {run.duplicateCount} dup · {run.notifiedCount} notified
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {new Date(run.startedAt).toLocaleString()}
                    </p>
                  </div>
                  <Badge
                    variant={
                      run.status === 'SUCCESS'
                        ? 'success'
                        : run.status === 'FAILED'
                          ? 'destructive'
                          : 'warning'
                    }
                  >
                    {run.status}
                  </Badge>
                </div>
              ))
            ) : (
              <p className="text-sm text-muted-foreground">No scheduler runs yet.</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
