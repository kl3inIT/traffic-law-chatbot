'use client';

import { useState, useMemo } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { DataTable } from '@/components/admin/data-table';
import { createParameterColumns } from '@/components/admin/parameters/columns';
import { ParameterDialog } from '@/components/admin/parameters/parameter-dialog';
import { useParameterSets } from '@/hooks/use-parameters';
import type { AiParameterSetResponse } from '@/types/api';

export default function ParametersPage() {
  const { data: parameterSets, isLoading, isError } = useParameterSets();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<AiParameterSetResponse | null>(null);

  const handleCreate = () => {
    setEditTarget(null);
    setDialogOpen(true);
  };

  const handleEdit = (paramSet: AiParameterSetResponse) => {
    setEditTarget(paramSet);
    setDialogOpen(true);
  };

  const columns = useMemo(() => createParameterColumns(handleEdit), []);

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Bộ tham số AI</h1>
        <Button variant="default" onClick={handleCreate}>
          <Plus className="h-4 w-4 mr-2" />
          Tạo bộ tham số
        </Button>
      </div>

      {isError && (
        <Alert variant="destructive">
          <AlertDescription>Không thể tải dữ liệu. Vui lòng thử lại.</AlertDescription>
        </Alert>
      )}

      <DataTable
        columns={columns}
        data={parameterSets ?? []}
        isLoading={isLoading}
      />

      {parameterSets && parameterSets.length === 0 && !isLoading && (
        <div className="text-center py-8">
          <h2 className="text-xl font-semibold">Chưa có bộ tham số</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Tạo bộ tham số để cấu hình hành vi AI.
          </p>
        </div>
      )}

      <ParameterDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        editTarget={editTarget}
      />
    </div>
  );
}
