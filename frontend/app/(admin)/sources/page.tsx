'use client';

import { DataTable } from '@/components/admin/data-table';
import { columns } from '@/components/admin/sources/columns';
import { useSources } from '@/hooks/use-sources';
import { Alert, AlertDescription } from '@/components/ui/alert';

export default function SourcesPage() {
  const { data, isLoading, isError } = useSources();

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-xl font-semibold">Quan ly nguon</h1>

      {isError && (
        <Alert variant="destructive">
          <AlertDescription>Khong the tai du lieu. Vui long thu lai.</AlertDescription>
        </Alert>
      )}

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        isLoading={isLoading}
      />

      {data && data.content.length === 0 && !isLoading && (
        <div className="text-center py-8">
          <h2 className="text-xl font-semibold">Chua co nguon tai lieu</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Them nguon tai lieu de xay dung co so kien thuc.
          </p>
        </div>
      )}
    </div>
  );
}
