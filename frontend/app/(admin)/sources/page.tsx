'use client';

import { DataTable } from '@/components/admin/data-table';
import { columns } from '@/components/admin/sources/columns';
import { useSources } from '@/hooks/use-sources';
import { Alert, AlertDescription } from '@/components/ui/alert';

export default function SourcesPage() {
  const { data, isLoading, isError } = useSources();

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-xl font-semibold">Quản lý nguồn</h1>

      {isError && (
        <Alert variant="destructive">
          <AlertDescription>Không thể tải dữ liệu. Vui lòng thử lại.</AlertDescription>
        </Alert>
      )}

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        isLoading={isLoading}
      />

      {data && data.content.length === 0 && !isLoading && (
        <div className="text-center py-8">
          <h2 className="text-xl font-semibold">Chưa có nguồn tài liệu</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Thêm nguồn tài liệu để xây dựng cơ sở kiến thức.
          </p>
        </div>
      )}
    </div>
  );
}
