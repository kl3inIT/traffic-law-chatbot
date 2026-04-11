'use client';

import { ColumnDef } from '@tanstack/react-table';
import { Badge } from '@/components/ui/badge';
import type { AiParameterSetResponse } from '@/types/api';
import { ParameterActionsMenu } from './parameters-table';

export function createParameterColumns(
  onEdit: (paramSet: AiParameterSetResponse) => void
): ColumnDef<AiParameterSetResponse>[] {
  return [
    {
      accessorKey: 'name',
      header: 'Tên',
      cell: ({ row }) => (
        <div className="flex items-center gap-2">
          <span>{row.original.name}</span>
          {row.original.active && (
            <Badge variant="default">Đang hoạt động</Badge>
          )}
        </div>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Ngày tạo',
      cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('vi-VN'),
    },
    {
      accessorKey: 'updatedAt',
      header: 'Cập nhật',
      cell: ({ row }) => new Date(row.original.updatedAt).toLocaleDateString('vi-VN'),
    },
    {
      id: 'actions',
      header: 'Hành động',
      cell: ({ row }) => (
        <ParameterActionsMenu
          paramSet={row.original}
          onEdit={onEdit}
        />
      ),
    },
  ];
}
