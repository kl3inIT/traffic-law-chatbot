'use client';

import { RefreshCw } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { Skeleton } from '@/components/ui/skeleton';
import { useChunkReadiness, useIndexSummary } from '@/hooks/use-index';

export function IndexCards() {
  const readiness = useChunkReadiness();
  const summary = useIndexSummary();

  const handleRefresh = () => {
    readiness.refetch();
    summary.refetch();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Chi muc kien thuc</h1>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger render={<Button variant="ghost" size="icon" onClick={handleRefresh} disabled={readiness.isFetching || summary.isFetching} />}>
              <RefreshCw className={`h-4 w-4 ${readiness.isFetching ? 'animate-spin' : ''}`} />
            </TooltipTrigger>
            <TooltipContent>Lam moi du lieu</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {/* Retrieval Readiness card -- dominant (first/left) per UI-SPEC */}
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-semibold">San sang truy xuat</CardTitle>
          </CardHeader>
          <CardContent>
            {readiness.isLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-6 w-32" />
                <Skeleton className="h-6 w-24" />
              </div>
            ) : readiness.data ? (
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Da phe duyet</dt>
                  <dd className="font-semibold">{readiness.data.approvedCount}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Duoc tin cay</dt>
                  <dd className="font-semibold">{readiness.data.trustedCount}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Dang hoat dong</dt>
                  <dd className="font-semibold">{readiness.data.activeCount}</dd>
                </div>
                <div className="flex justify-between border-t pt-2">
                  <dt className="font-semibold">Du dieu kien truy xuat</dt>
                  <dd className="font-semibold text-primary">{readiness.data.eligibleCount}</dd>
                </div>
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">Chua co du lieu chi muc</p>
            )}
          </CardContent>
        </Card>

        {/* Index Summary card */}
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-semibold">Tom tat chi muc</CardTitle>
          </CardHeader>
          <CardContent>
            {summary.isLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-6 w-32" />
                <Skeleton className="h-6 w-24" />
              </div>
            ) : summary.data ? (
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Tong so chunk</dt>
                  <dd className="font-semibold">{summary.data.totalChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Dang hoat dong</dt>
                  <dd className="font-semibold">{summary.data.activeChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Khong hoat dong</dt>
                  <dd className="font-semibold">{summary.data.inactiveChunks}</dd>
                </div>
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">Chua co du lieu chi muc</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
