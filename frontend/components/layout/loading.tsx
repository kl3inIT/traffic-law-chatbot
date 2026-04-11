import { Skeleton } from '@/components/ui/skeleton';

interface LoadingProps {
  rows?: number;
  className?: string;
}

export function Loading({ rows = 3, className }: LoadingProps) {
  return (
    <div className={`space-y-3 p-6 ${className ?? ''}`}>
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-8 w-full" />
      ))}
    </div>
  );
}

export function PageLoading() {
  return (
    <div className="flex min-h-[300px] items-center justify-center">
      <div className="w-full max-w-md space-y-3 px-6">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </div>
    </div>
  );
}
