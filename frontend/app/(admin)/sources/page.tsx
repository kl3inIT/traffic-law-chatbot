'use client';

import { useState } from 'react';
import { DataTable } from '@/components/admin/data-table';
import { columns } from '@/components/admin/sources/columns';
import { AddSourceDialog } from '@/components/admin/sources/add-source-dialog';
import { QueryBoundary } from '@/components/layout/query-boundary';
import { Button } from '@/components/ui/button';
import {
  useSources,
  useBulkApproveSource,
  useBulkRejectSource,
  useBulkActivateSource,
} from '@/hooks/use-sources';
import type { SourceSummaryResponse } from '@/types/api';

const PAGE_SIZE = 20;

export default function SourcesPage() {
  const [page, setPage] = useState(0);
  const [selectedSources, setSelectedSources] = useState<SourceSummaryResponse[]>([]);
  const { data, isLoading, isError, error, refetch } = useSources(page, PAGE_SIZE);
  const bulkApprove = useBulkApproveSource();
  const bulkReject = useBulkRejectSource();
  const bulkActivate = useBulkActivateSource();

  const totalPages = data?.totalPages ?? 0;
  const noneSelected = selectedSources.length === 0;
  const pendingSelected = selectedSources.filter((s) => s.approvalState === 'PENDING');
  const activatableSelected = selectedSources.filter(
    (s) => s.approvalState === 'APPROVED' && s.status !== 'ACTIVE',
  );

  const handleBulkApprove = () => {
    if (!pendingSelected.length) return;
    bulkApprove.mutate(
      pendingSelected.map((s) => s.id),
      {
        onSuccess: () => setSelectedSources([]),
      },
    );
  };

  const handleBulkReject = () => {
    if (!pendingSelected.length) return;
    bulkReject.mutate(
      pendingSelected.map((s) => s.id),
      {
        onSuccess: () => setSelectedSources([]),
      },
    );
  };

  const handleBulkActivate = () => {
    if (!activatableSelected.length) return;
    bulkActivate.mutate(
      activatableSelected.map((s) => s.id),
      {
        onSuccess: () => setSelectedSources([]),
      },
    );
  };

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Quản lý nguồn</h1>
        <AddSourceDialog />
      </div>

      <div className="bg-muted flex items-center gap-3 rounded-md border px-4 py-2">
        <span className="text-muted-foreground text-sm">
          {noneSelected
            ? 'Chọn nguồn để thao tác hàng loạt'
            : `Đã chọn ${selectedSources.length} nguồn`}
        </span>
        <Button
          size="sm"
          disabled={noneSelected || !pendingSelected.length || bulkApprove.isPending}
          onClick={handleBulkApprove}
        >
          {bulkApprove.isPending
            ? 'Đang duyệt...'
            : `Phê duyệt${pendingSelected.length > 0 ? ` (${pendingSelected.length})` : ''}`}
        </Button>
        <Button
          size="sm"
          variant="outline"
          className="border-green-200 text-green-700 hover:bg-green-50 disabled:opacity-40"
          disabled={noneSelected || !activatableSelected.length || bulkActivate.isPending}
          onClick={handleBulkActivate}
        >
          {bulkActivate.isPending
            ? 'Đang kích hoạt...'
            : `Kích hoạt${activatableSelected.length > 0 ? ` (${activatableSelected.length})` : ''}`}
        </Button>
        <Button
          size="sm"
          variant="destructive"
          disabled={noneSelected || !pendingSelected.length || bulkReject.isPending}
          onClick={handleBulkReject}
        >
          {bulkReject.isPending
            ? 'Đang từ chối...'
            : `Từ chối${pendingSelected.length > 0 ? ` (${pendingSelected.length})` : ''}`}
        </Button>
      </div>

      <QueryBoundary
        isLoading={isLoading}
        isError={isError}
        error={error as Error | null}
        onRetry={refetch}
      >
        {/* key resets row selection when page changes */}
        <DataTable
          key={page}
          columns={columns}
          data={data?.content ?? []}
          isLoading={false}
          enableRowSelection
          onSelectionChange={setSelectedSources}
        />

        {data && data.content.length === 0 && (
          <div className="py-8 text-center">
            <h2 className="text-xl font-semibold">Chưa có nguồn tài liệu</h2>
            <p className="text-muted-foreground mt-2 text-sm">
              Thêm nguồn tài liệu để xây dựng cơ sở kiến thức.
            </p>
          </div>
        )}

        {totalPages > 1 && (
          <div className="text-muted-foreground flex items-center justify-between text-xs">
            <span>
              Trang {page + 1} / {totalPages} ({data?.totalElements ?? 0} nguồn)
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-3 text-xs"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Trước
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-3 text-xs"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Sau
              </Button>
            </div>
          </div>
        )}
      </QueryBoundary>
    </div>
  );
}
