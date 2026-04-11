'use client';

import { ColumnDef } from '@tanstack/react-table';
import { Badge } from '@/components/ui/badge';
import type { SourceSummaryResponse, SourceStatus } from '@/types/api';
import { SourceActionsMenu } from './sources-table';

// Status badge colors per UI-SPEC
const statusBadgeClasses: Record<SourceStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-blue-100 text-blue-800',
  ACTIVE: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
};

const statusLabels: Record<SourceStatus, string> = {
  PENDING: 'Cho duyet',
  APPROVED: 'Da duyet',
  ACTIVE: 'Dang hoat dong',
  REJECTED: 'Tu choi',
};

export const columns: ColumnDef<SourceSummaryResponse>[] = [
  {
    accessorKey: 'title',
    header: 'Tieu de / URL',
    cell: ({ row }) => (
      <div className="max-w-[300px] truncate">{row.original.title}</div>
    ),
  },
  {
    accessorKey: 'sourceType',
    header: 'Loai nguon',
  },
  {
    accessorKey: 'status',
    header: 'Trang thai',
    cell: ({ row }) => {
      const status = row.original.status;
      return (
        <Badge className={statusBadgeClasses[status]}>
          {statusLabels[status]}
        </Badge>
      );
    },
  },
  {
    accessorKey: 'createdAt',
    header: 'Ngay tao',
    cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('vi-VN'),
  },
  {
    id: 'actions',
    header: 'Hanh dong',
    cell: ({ row }) => <SourceActionsMenu source={row.original} />,
  },
];
