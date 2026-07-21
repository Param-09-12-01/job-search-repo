import { useState, type DragEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { ExternalLink, Plus, Trash2 } from 'lucide-react';
import { applicationService } from '@/services';
import { extractErrorMessage } from '@/services/api';
import { useToast } from '@/contexts/ToastContext';
import { KANBAN_COLUMNS, APPLICATION_STATUSES } from '@/constants';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { Label } from '@/components/ui/Label';
import { cn } from '@/utils/cn';
import type { Application, ApplicationStatus, ManualApplicationRequest } from '@/types';

export function ApplicationsPage() {
  const qc = useQueryClient();
  const { success, error } = useToast();
  const [dragOver, setDragOver] = useState<ApplicationStatus | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Application | null>(null);
  const [showManual, setShowManual] = useState(false);
  const [manualForm, setManualForm] = useState<ManualApplicationRequest>({ title: '', status: 'APPLIED' });

  const { data: board, isLoading } = useQuery({
    queryKey: ['applications', 'board'],
    queryFn: applicationService.board,
  });

  const moveMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ApplicationStatus }) =>
      applicationService.update(id, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['applications'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => error('Move failed', extractErrorMessage(e)),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => applicationService.remove(id),
    onSuccess: () => {
      success('Application removed');
      qc.invalidateQueries({ queryKey: ['applications'] });
    },
    onError: (e) => error('Delete failed', extractErrorMessage(e)),
  });

  const createManualMutation = useMutation({
    mutationFn: (req: ManualApplicationRequest) => applicationService.createManual(req),
    onSuccess: () => {
      success('Manual application added');
      setShowManual(false);
      setManualForm({ title: '', status: 'APPLIED' });
      qc.invalidateQueries({ queryKey: ['applications'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (e) => error('Failed to add', extractErrorMessage(e)),
  });

  const onDrop = (e: DragEvent, status: ApplicationStatus) => {
    e.preventDefault();
    setDragOver(null);
    const id = Number(e.dataTransfer.getData('text/plain'));
    const current = findStatus(board, id);
    if (id && current !== status) {
      moveMutation.mutate({ id, status });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Application Tracker</h1>
          <p className="text-sm text-muted-foreground">Drag cards between columns to update status</p>
        </div>
        <Button onClick={() => setShowManual(true)}>
          <Plus className="h-4 w-4 mr-1" />
          Add Manual
        </Button>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {KANBAN_COLUMNS.map((c) => (
            <Skeleton key={c.key} className="h-96 w-full" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {KANBAN_COLUMNS.map((col) => {
            const items = board?.[col.key] ?? [];
            return (
              <div
                key={col.key}
                onDragOver={(e) => {
                  e.preventDefault();
                  setDragOver(col.key);
                }}
                onDragLeave={() => setDragOver((s) => (s === col.key ? null : s))}
                onDrop={(e) => onDrop(e, col.key)}
                className={cn(
                  'flex w-full flex-col rounded-lg border bg-card/50 transition-colors',
                  dragOver === col.key && 'border-primary bg-primary/5',
                )}
              >
                <div className="flex items-center justify-between border-b px-4 py-3">
                  <span className="text-sm font-semibold">{col.label}</span>
                  <Badge variant="secondary">{items.length}</Badge>
                </div>
                <div className="flex-1 space-y-2 p-2">
                  {items.map((app) => (
                    <motion.div
                      layout
                      key={app.id}
                      draggable
                      onDragStart={(e) =>
                        (e as unknown as DragEvent).dataTransfer.setData('text/plain', String(app.id))
                      }
                      className="cursor-grab rounded-md border bg-card p-3 shadow-sm active:cursor-grabbing"
                    >
                      <p className="text-sm font-medium leading-snug">{app.posting.title}</p>
                      <p className="mt-1 text-xs text-muted-foreground">{app.posting.company}</p>
                      <div className="mt-2 flex items-center justify-between">
                        <Badge variant="outline">{app.posting.score}</Badge>
                        <div className="flex gap-1">
                          <Button variant="ghost" size="icon" className="h-7 w-7" asChild>
                            <a href={app.posting.url} target="_blank" rel="noopener noreferrer">
                              <ExternalLink className="h-3.5 w-3.5" />
                            </a>
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-muted-foreground"
                            onClick={() => setDeleteTarget(app)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </div>
                    </motion.div>
                  ))}
                  {items.length === 0 && (
                    <p className="px-2 py-6 text-center text-xs text-muted-foreground">
                      Drop cards here
                    </p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(o) => !o && setDeleteTarget(null)}
        title="Remove this application?"
        description="This removes it from the tracker. The posting itself is not deleted."
        confirmLabel="Remove"
        destructive
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
      />

      <Dialog open={showManual} onOpenChange={(o) => { if (!o) { setShowManual(false); setManualForm({ title: '', status: 'APPLIED' }); } }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Add Manual Application</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!manualForm.title.trim()) return;
              createManualMutation.mutate(manualForm);
            }}
            className="space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="title">Title *</Label>
              <Input
                id="title"
                value={manualForm.title}
                onChange={(e) => setManualForm({ ...manualForm, title: e.target.value })}
                required
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="company">Company</Label>
                <Input
                  id="company"
                  value={manualForm.company ?? ''}
                  onChange={(e) => setManualForm({ ...manualForm, company: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="location">Location</Label>
                <Input
                  id="location"
                  value={manualForm.location ?? ''}
                  onChange={(e) => setManualForm({ ...manualForm, location: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="url">URL</Label>
              <Input
                id="url"
                type="url"
                value={manualForm.url ?? ''}
                onChange={(e) => setManualForm({ ...manualForm, url: e.target.value })}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="salary">Salary</Label>
                <Input
                  id="salary"
                  value={manualForm.salary ?? ''}
                  onChange={(e) => setManualForm({ ...manualForm, salary: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="status">Status</Label>
                <Select
                  id="status"
                  value={manualForm.status ?? 'APPLIED'}
                  onChange={(e) => setManualForm({ ...manualForm, status: e.target.value as ApplicationStatus })}
                >
                  {APPLICATION_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s.charAt(0) + s.slice(1).toLowerCase()}
                    </option>
                  ))}
                </Select>
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="notes">Notes</Label>
              <Textarea
                id="notes"
                value={manualForm.notes ?? ''}
                onChange={(e) => setManualForm({ ...manualForm, notes: e.target.value })}
                rows={3}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setShowManual(false); setManualForm({ title: '', status: 'APPLIED' }); }}>
                Cancel
              </Button>
              <Button type="submit" disabled={createManualMutation.isPending || !manualForm.title.trim()}>
                {createManualMutation.isPending ? 'Saving...' : 'Add'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function findStatus(
  board: Record<ApplicationStatus, Application[]> | undefined,
  id: number,
): ApplicationStatus | null {
  if (!board) return null;
  for (const [status, apps] of Object.entries(board) as [ApplicationStatus, Application[]][]) {
    if (apps.some((a) => a.id === id)) return status;
  }
  return null;
}
