import { useState } from 'react';
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { AnimatePresence } from 'framer-motion';
import { Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { jobService, applicationService, type JobQuery } from '@/services';
import { extractErrorMessage } from '@/services/api';
import { useToast } from '@/contexts/ToastContext';
import { JobCard } from '@/components/JobCard';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import type { Posting } from '@/types';

export function JobsPage() {
  const qc = useQueryClient();
  const { success, error } = useToast();

  const [filters, setFilters] = useState<JobQuery>({
    page: 0,
    size: 12,
    sortBy: 'score',
    sortDir: 'desc',
  });
  const [searchInput, setSearchInput] = useState('');
  const [preparingId, setPreparingId] = useState<number | null>(null);
  const [dismissTarget, setDismissTarget] = useState<Posting | null>(null);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['jobs', filters],
    queryFn: () => jobService.list(filters),
    placeholderData: keepPreviousData,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['jobs'] });

  const saveMutation = useMutation({
    mutationFn: (job: Posting) =>
      job.saved ? jobService.unsave(job.id) : jobService.save(job.id),
    onSuccess: (_, job) => {
      success(job.saved ? 'Removed from saved' : 'Job saved');
      invalidate();
    },
    onError: (e) => error('Action failed', extractErrorMessage(e)),
  });

  const dismissMutation = useMutation({
    mutationFn: (job: Posting) => jobService.dismiss(job.id),
    onSuccess: () => {
      success('Job dismissed');
      invalidate();
    },
    onError: (e) => error('Action failed', extractErrorMessage(e)),
  });

  const trackMutation = useMutation({
    mutationFn: (job: Posting) => applicationService.create(job.id),
    onSuccess: () => {
      success('Added to application tracker');
      invalidate();
      qc.invalidateQueries({ queryKey: ['applications'] });
    },
    onError: (e) => error('Could not track', extractErrorMessage(e)),
  });

  const prepareMutation = useMutation({
    mutationFn: (job: Posting) => jobService.prepare(job.id),
    onMutate: (job) => setPreparingId(job.id),
    onSuccess: (res) => success('Application prepared', res.message),
    onError: (e) => error('Prepare failed', extractErrorMessage(e)),
    onSettled: () => setPreparingId(null),
  });

  const applySearch = () => setFilters((f) => ({ ...f, query: searchInput || undefined, page: 0 }));

  const totalPages = data?.totalPages ?? 0;
  const page = data?.page ?? 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Jobs</h1>
        <p className="text-sm text-muted-foreground">
          {data ? `${data.totalElements} postings` : 'Browse and manage matched postings'}
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="Search title, company, location…"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && applySearch()}
          />
        </div>
        <Select
          className="w-auto"
          value={filters.remote === undefined ? '' : String(filters.remote)}
          onChange={(e) =>
            setFilters((f) => ({
              ...f,
              remote: e.target.value === '' ? undefined : e.target.value === 'true',
              page: 0,
            }))
          }
        >
          <option value="">All locations</option>
          <option value="true">Remote only</option>
          <option value="false">On-site only</option>
        </Select>
        <Select
          className="w-auto"
          value={filters.minScore ?? ''}
          onChange={(e) =>
            setFilters((f) => ({
              ...f,
              minScore: e.target.value ? Number(e.target.value) : undefined,
              page: 0,
            }))
          }
        >
          <option value="">Any score</option>
          <option value="80">80+</option>
          <option value="60">60+</option>
          <option value="40">40+</option>
        </Select>
        <Select
          className="w-auto"
          value={`${filters.sortBy}:${filters.sortDir}`}
          onChange={(e) => {
            const [sortBy, sortDir] = e.target.value.split(':');
            setFilters((f) => ({ ...f, sortBy, sortDir, page: 0 }));
          }}
        >
          <option value="score:desc">Score (high→low)</option>
          <option value="postedAt:desc">Newest</option>
          <option value="company:asc">Company (A→Z)</option>
          <option value="title:asc">Title (A→Z)</option>
        </Select>
        <Button onClick={applySearch}>Search</Button>
      </div>

      {/* Job grid */}
      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-56" />
          ))}
        </div>
      ) : data && data.content.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <AnimatePresence mode="popLayout">
            {data.content.map((job) => (
              <JobCard
                key={job.id}
                job={job}
                preparing={preparingId === job.id}
                onSave={(j) => saveMutation.mutate(j)}
                onDismiss={(j) => setDismissTarget(j)}
                onPrepare={(j) => prepareMutation.mutate(j)}
                onTrack={(j) => trackMutation.mutate(j)}
              />
            ))}
          </AnimatePresence>
        </div>
      ) : (
        <div className="rounded-lg border border-dashed p-12 text-center text-muted-foreground">
          No postings found. Adjust your filters or run the scheduler.
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-4">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 0 || isFetching}
            onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) - 1 }))}
          >
            <ChevronLeft className="h-4 w-4" /> Prev
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1 || isFetching}
            onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) + 1 }))}
          >
            Next <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={!!dismissTarget}
        onOpenChange={(o) => !o && setDismissTarget(null)}
        title="Dismiss this posting?"
        description="It will be hidden from your job list. You can re-include dismissed jobs via filters."
        confirmLabel="Dismiss"
        destructive
        onConfirm={() => dismissTarget && dismissMutation.mutate(dismissTarget)}
      />
    </div>
  );
}
