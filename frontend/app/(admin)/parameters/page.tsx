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

  // Create columns with onEdit handler -- no onCopy override.
  // Copy is handled entirely by ParameterActionsMenu calling copy.mutate()
  // which hits POST /{id}/copy on the backend (per D-14).
  const columns = useMemo(() => createParameterColumns(handleEdit), []);

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Bo tham so AI</h1>
        <Button variant="default" onClick={handleCreate}>
          <Plus className="h-4 w-4 mr-2" />
          Tao bo tham so
        </Button>
      </div>

      {isError && (
        <Alert variant="destructive">
          <AlertDescription>Khong the tai du lieu. Vui long thu lai.</AlertDescription>
        </Alert>
      )}

      <DataTable
        columns={columns}
        data={parameterSets ?? []}
        isLoading={isLoading}
      />

      {parameterSets && parameterSets.length === 0 && !isLoading && (
        <div className="text-center py-8">
          <h2 className="text-xl font-semibold">Chua co bo tham so</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Tao bo tham so de cau hinh hanh vi AI.
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
