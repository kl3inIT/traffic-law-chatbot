'use client';

import { DataTable } from '@/components/admin/data-table';
import { columns } from '@/components/admin/sources/columns';
import { AddSourceDialog } from '@/components/admin/sources/add-source-dialog';
import { QueryBoundary } from '@/components/layout/query-boundary';
import { useSources } from '@/hooks/use-sources';

export default function SourcesPage() {
  const { data, isLoading, isError, error, refetch } = useSources();

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Quản lý nguồn</h1>
        <AddSourceDialog />
      </div>

      <QueryBoundary
        isLoading={isLoading}
        isError={isError}
        error={error as Error | null}
        onRetry={refetch}
      >
        <DataTable columns={columns} data={data?.content ?? []} isLoading={false} />

        {data && data.content.length === 0 && (
          <div className="py-8 text-center">
            <h2 className="text-xl font-semibold">Chưa có nguồn tài liệu</h2>
            <p className="text-muted-foreground mt-2 text-sm">
              Thêm nguồn tài liệu để xây dựng cơ sở kiến thức.
            </p>
          </div>
        )}
      </QueryBoundary>
    </div>
  );
}
