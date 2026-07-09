import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Mail, Send, CheckCheck } from 'lucide-react';
import { notificationService } from '@/services';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';

export function NotificationsPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', 'list', page],
    queryFn: () => notificationService.list(page, 20),
  });

  const markRead = useMutation({
    mutationFn: (id: number) => notificationService.markRead(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Notifications</h1>
        <p className="text-sm text-muted-foreground">Delivery history across all channels</p>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <div className="space-y-3">
          {data.content.map((n) => (
            <Card key={n.id} className={n.read ? 'opacity-70' : ''}>
              <CardContent className="flex items-start justify-between gap-4 p-4">
                <div className="flex gap-3">
                  {n.channel === 'EMAIL' ? (
                    <Mail className="mt-0.5 h-5 w-5 text-primary" />
                  ) : (
                    <Send className="mt-0.5 h-5 w-5 text-primary" />
                  )}
                  <div>
                    <p className="text-sm font-medium">{n.title}</p>
                    <p className="mt-0.5 whitespace-pre-line text-sm text-muted-foreground">
                      {n.message}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {new Date(n.createdAt).toLocaleString()}
                    </p>
                  </div>
                </div>
                <div className="flex flex-col items-end gap-2">
                  <Badge
                    variant={
                      n.status === 'SENT' ? 'success' : n.status === 'FAILED' ? 'destructive' : 'secondary'
                    }
                  >
                    {n.status}
                  </Badge>
                  {!n.read && (
                    <Button variant="ghost" size="sm" onClick={() => markRead.mutate(n.id)}>
                      <CheckCheck className="h-4 w-4" /> Mark read
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed p-12 text-center text-muted-foreground">
          No notifications yet.
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-4">
          <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            Prev
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
