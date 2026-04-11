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
        <h1 className="text-xl font-semibold">Chỉ mục kiến thức</h1>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={handleRefresh}
                  disabled={readiness.isFetching || summary.isFetching}
                />
              }
            >
              <RefreshCw className={`h-4 w-4 ${readiness.isFetching ? 'animate-spin' : ''}`} />
            </TooltipTrigger>
            <TooltipContent>Làm mới dữ liệu</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-semibold">Sẵn sàng truy xuất</CardTitle>
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
                  <dt className="text-muted-foreground">Đã phê duyệt</dt>
                  <dd className="font-semibold">{readiness.data.approvedChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Được tin cậy</dt>
                  <dd className="font-semibold">{readiness.data.trustedChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Đang hoạt động</dt>
                  <dd className="font-semibold">{readiness.data.activeChunks}</dd>
                </div>
                <div className="flex justify-between border-t pt-2">
                  <dt className="font-semibold">Đủ điều kiện truy xuất</dt>
                  <dd className="text-primary font-semibold">{readiness.data.eligibleChunks}</dd>
                </div>
              </dl>
            ) : (
              <p className="text-muted-foreground text-sm">Chưa có dữ liệu chỉ mục</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-semibold">Tóm tắt chỉ mục</CardTitle>
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
                  <dt className="text-muted-foreground">Tổng số đoạn văn bản</dt>
                  <dd className="font-semibold">{summary.data.totalChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Đang hoạt động</dt>
                  <dd className="font-semibold">{summary.data.activeChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Đủ điều kiện truy xuất</dt>
                  <dd className="text-primary font-semibold">{summary.data.eligibleChunks}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Chờ phê duyệt</dt>
                  <dd className="font-semibold">{summary.data.pendingApprovalChunks}</dd>
                </div>
              </dl>
            ) : (
              <p className="text-muted-foreground text-sm">Chưa có dữ liệu chỉ mục</p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
