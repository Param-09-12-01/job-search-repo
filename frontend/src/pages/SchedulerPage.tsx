import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Play, Loader2, ExternalLink, ChevronRight } from 'lucide-react';
import { schedulerService } from '@/services';
import { extractErrorMessage } from '@/services/api';
import { useToast } from '@/contexts/ToastContext';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import type { SchedulerLog } from '@/types';

function duration(start: string, end?: string): string {
  if (!end) return '\u2014';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  return `${(ms / 1000).toFixed(1)}s`;
}

function RunJobsDialog({ run, open, onClose }: { run: SchedulerLog; open: boolean; onClose: () => void }) {
  const { data: jobs, isLoading } = useQuery({
    queryKey: ['scheduler', 'run', run.id, 'jobs'],
    queryFn: () => schedulerService.jobsForRun(run.id),
    enabled: open,
  });

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            Jobs fetched &mdash; {new Date(run.startedAt).toLocaleString()}
          </DialogTitle>
        </DialogHeader>
        {isLoading ? (
          <div className="space-y-2 py-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : jobs && jobs.length > 0 ? (
          <div className="divide-y">
            {jobs.map((job) => (
              <div key={job.id} className="flex items-start gap-3 py-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <a
                      href={job.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-medium truncate hover:underline"
                    >
                      {job.title}
                    </a>
                    <ExternalLink className="h-3 w-3 shrink-0 text-muted-foreground" />
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {job.company}{job.location ? ` \u00b7 ${job.location}` : ''}
                  </div>
                </div>
                <Badge variant={job.score >= 50 ? 'success' : 'secondary'} className="shrink-0">
                  {job.score}
                </Badge>
              </div>
            ))}
          </div>
        ) : (
          <p className="py-8 text-center text-muted-foreground">No jobs were stored in this run.</p>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function SchedulerPage() {
  const qc = useQueryClient();
  const { success, error } = useToast();
  const [selectedRun, setSelectedRun] = useState<SchedulerLog | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['scheduler', 'logs'],
    queryFn: () => schedulerService.logs(0, 20),
    refetchInterval: (query) => {
      const logs = query.state.data?.content;
      return logs?.some((r) => r.status === 'RUNNING') ? 5000 : false;
    },
  });

  const runNow = useMutation({
    mutationFn: schedulerService.runNow,
    onSuccess: (log) => {
      success('Ingestion run complete', `${log.newCount} new \u00b7 ${log.notifiedCount} notified`);
      qc.invalidateQueries({ queryKey: ['scheduler'] });
      qc.invalidateQueries({ queryKey: ['jobs'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => error('Run failed', extractErrorMessage(e)),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Scheduler</h1>
          <p className="text-sm text-muted-foreground">Background ingestion run history</p>
        </div>
        <Button onClick={() => runNow.mutate()} disabled={runNow.isPending}>
          {runNow.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
          Run now
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <Card>
          <CardContent className="p-0">
            <table className="w-full text-sm">
              <thead className="border-b text-left text-muted-foreground">
                <tr>
                  <th className="p-3 font-medium">Started</th>
                  <th className="p-3 font-medium">Status</th>
                  <th className="p-3 font-medium">Fetched</th>
                  <th className="p-3 font-medium">New</th>
                  <th className="p-3 font-medium">Dup</th>
                  <th className="p-3 font-medium">Notified</th>
                  <th className="p-3 font-medium">Duration</th>
                  <th className="p-3 w-10" />
                </tr>
              </thead>
              <tbody>
                {data.content.map((run) => (
                  <tr
                    key={run.id}
                    className="border-b last:border-0 cursor-pointer hover:bg-muted/50 transition-colors"
                    onClick={() => setSelectedRun(run)}
                  >
                    <td className="p-3">{new Date(run.startedAt).toLocaleString()}</td>
                    <td className="p-3">
                      {run.status === 'RUNNING' ? (
                        <Badge variant="warning" className="inline-flex items-center gap-1">
                          <Loader2 className="h-3 w-3 animate-spin" />
                          RUNNING
                        </Badge>
                      ) : (
                        <Badge
                          variant={
                            run.status === 'SUCCESS'
                              ? 'success'
                              : 'destructive'
                          }
                        >
                          {run.status}
                        </Badge>
                      )}
                    </td>
                    <td className="p-3">{run.fetchedCount}</td>
                    <td className="p-3">{run.newCount}</td>
                    <td className="p-3">{run.duplicateCount}</td>
                    <td className="p-3">{run.notifiedCount}</td>
                    <td className="p-3">{duration(run.startedAt, run.finishedAt)}</td>
                    <td className="p-3">
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      ) : (
        <div className="rounded-lg border border-dashed p-12 text-center text-muted-foreground">
          No scheduler runs yet. Trigger one with \u201cRun now\u201d.
        </div>
      )}

      {selectedRun && (
        <RunJobsDialog
          run={selectedRun}
          open={!!selectedRun}
          onClose={() => setSelectedRun(null)}
        />
      )}
    </div>
  );
}
