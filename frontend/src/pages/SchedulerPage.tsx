import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Play, Loader2 } from 'lucide-react';
import { schedulerService } from '@/services';
import { extractErrorMessage } from '@/services/api';
import { useToast } from '@/contexts/ToastContext';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';

function duration(start: string, end?: string): string {
  if (!end) return '—';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  return `${(ms / 1000).toFixed(1)}s`;
}

export function SchedulerPage() {
  const qc = useQueryClient();
  const { success, error } = useToast();

  const { data, isLoading } = useQuery({
    queryKey: ['scheduler', 'logs'],
    queryFn: () => schedulerService.logs(0, 20),
  });

  const runNow = useMutation({
    mutationFn: schedulerService.runNow,
    onSuccess: (log) => {
      success('Ingestion run complete', `${log.newCount} new · ${log.notifiedCount} notified`);
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
                </tr>
              </thead>
              <tbody>
                {data.content.map((run) => (
                  <tr key={run.id} className="border-b last:border-0">
                    <td className="p-3">{new Date(run.startedAt).toLocaleString()}</td>
                    <td className="p-3">
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
                    </td>
                    <td className="p-3">{run.fetchedCount}</td>
                    <td className="p-3">{run.newCount}</td>
                    <td className="p-3">{run.duplicateCount}</td>
                    <td className="p-3">{run.notifiedCount}</td>
                    <td className="p-3">{duration(run.startedAt, run.finishedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      ) : (
        <div className="rounded-lg border border-dashed p-12 text-center text-muted-foreground">
          No scheduler runs yet. Trigger one with “Run now”.
        </div>
      )}
    </div>
  );
}
