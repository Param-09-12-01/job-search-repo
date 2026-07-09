import { useQuery } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import {
  Briefcase,
  Bookmark,
  Send,
  Users,
  XCircle,
  Trophy,
  Database,
} from 'lucide-react';
import { dashboardService } from '@/services';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { cn } from '@/utils/cn';
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
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: dashboardService.get,
    refetchInterval: 60_000,
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
