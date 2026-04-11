'use client';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { PageLoading } from './loading';

interface QueryBoundaryProps {
  isLoading: boolean;
  isError: boolean;
  error?: Error | null;
  onRetry?: () => void;
  loadingFallback?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * QueryBoundary centralises loading/error rendering for TanStack Query results.
 * Wrap any data-dependent UI with this component to avoid duplicating
 * loading/error checks on every page.
 *
 * Usage:
 *   <QueryBoundary isLoading={isLoading} isError={isError} onRetry={refetch}>
 *     <MyContent data={data!} />
 *   </QueryBoundary>
 */
export function QueryBoundary({
  isLoading,
  isError,
  error,
  onRetry,
  loadingFallback,
  children,
}: QueryBoundaryProps) {
  if (isLoading) {
    return <>{loadingFallback ?? <PageLoading />}</>;
  }

  if (isError) {
    return (
      <div className="space-y-3 p-6">
        <Alert variant="destructive">
          <AlertDescription>
            {error?.message ?? 'Không thể tải dữ liệu. Vui lòng thử lại.'}
          </AlertDescription>
        </Alert>
        {onRetry && (
          <Button variant="outline" size="sm" onClick={onRetry}>
            Thử lại
          </Button>
        )}
      </div>
    );
  }

  return <>{children}</>;
}
