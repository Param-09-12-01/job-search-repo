import type { HTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

/** Animated placeholder used while data loads. */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('animate-pulse rounded-md bg-muted', className)} {...props} />;
}
