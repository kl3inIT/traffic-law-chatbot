'use client';

import { ColumnDef } from '@tanstack/react-table';
import { Badge } from '@/components/ui/badge';
import type { AiParameterSetResponse } from '@/types/api';
import { ParameterActionsMenu } from './parameters-table';

// Factory function to create columns with the onEdit callback
export function createParameterColumns(
  onEdit: (paramSet: AiParameterSetResponse) => void
): ColumnDef<AiParameterSetResponse>[] {
  return [
    {
      accessorKey: 'name',
      header: 'Ten',
      cell: ({ row }) => (
        <div className="flex items-center gap-2">
          <span>{row.original.name}</span>
          {row.original.active && (
            <Badge variant="default">Dang hoat dong</Badge>
          )}
        </div>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Ngay tao',
      cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('vi-VN'),
    },
    {
      accessorKey: 'updatedAt',
      header: 'Cap nhat',
      cell: ({ row }) => new Date(row.original.updatedAt).toLocaleDateString('vi-VN'),
    },
    {
      id: 'actions',
      header: 'Hanh dong',
      cell: ({ row }) => (
        <ParameterActionsMenu
          paramSet={row.original}
          onEdit={onEdit}
        />
      ),
    },
  ];
}
