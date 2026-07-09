import { motion } from 'framer-motion';
import { Bookmark, BookmarkCheck, ExternalLink, Wand2, X, MapPin, Building2 } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { cn } from '@/utils/cn';
import type { Posting } from '@/types';

interface JobCardProps {
  job: Posting;
  onSave: (job: Posting) => void;
  onDismiss: (job: Posting) => void;
  onPrepare: (job: Posting) => void;
  onTrack: (job: Posting) => void;
  preparing?: boolean;
}

function scoreColor(score: number): string {
  if (score >= 80) return 'text-green-500';
  if (score >= 60) return 'text-amber-500';
  if (score >= 40) return 'text-orange-500';
  return 'text-red-500';
}

export function JobCard({ job, onSave, onDismiss, onPrepare, onTrack, preparing }: JobCardProps) {
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.97 }}
    >
      <Card className="transition-shadow hover:shadow-md">
        <CardContent className="space-y-3 p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="truncate text-base font-semibold">{job.title}</h3>
              <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                {job.company && (
                  <span className="flex items-center gap-1">
                    <Building2 className="h-3.5 w-3.5" />
                    {job.company}
                  </span>
                )}
                {job.location && (
                  <span className="flex items-center gap-1">
                    <MapPin className="h-3.5 w-3.5" />
                    {job.location}
                  </span>
                )}
              </div>
            </div>
            <div className="flex flex-col items-end">
              <span className={cn('text-2xl font-bold', scoreColor(job.score))}>{job.score}</span>
              <span className="text-[10px] uppercase text-muted-foreground">score</span>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <Badge variant="outline">{job.source}</Badge>
            {job.remote && <Badge variant="success">Remote</Badge>}
            {job.salary && <Badge variant="secondary">{job.salary}</Badge>}
            {job.applied && <Badge>Applied</Badge>}
          </div>

          {job.description && (
            <p className="line-clamp-2 text-sm text-muted-foreground">{job.description}</p>
          )}

          <div className="flex flex-wrap items-center gap-2 pt-1">
            <Button
              variant={job.saved ? 'secondary' : 'outline'}
              size="sm"
              onClick={() => onSave(job)}
            >
              {job.saved ? <BookmarkCheck className="h-4 w-4" /> : <Bookmark className="h-4 w-4" />}
              {job.saved ? 'Saved' : 'Save'}
            </Button>
            <Button variant="outline" size="sm" onClick={() => onPrepare(job)} disabled={preparing}>
              <Wand2 className="h-4 w-4" />
              Prepare
            </Button>
            <Button variant="outline" size="sm" onClick={() => onTrack(job)} disabled={job.applied}>
              Track
            </Button>
            <Button variant="ghost" size="sm" asChild>
              <a href={job.url} target="_blank" rel="noopener noreferrer">
                <ExternalLink className="h-4 w-4" />
                Open
              </a>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="ml-auto text-muted-foreground"
              onClick={() => onDismiss(job)}
            >
              <X className="h-4 w-4" />
              Dismiss
            </Button>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}
