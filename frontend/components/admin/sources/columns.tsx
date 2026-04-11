'use client';

import { ColumnDef } from '@tanstack/react-table';
import { Badge } from '@/components/ui/badge';
import type { SourceSummaryResponse, SourceStatus, ApprovalState, TrustedState } from '@/types/api';
import { SourceActionsMenu } from './sources-table';
import { SourceIngestionDetailButton } from './source-ingestion-detail';

const statusBadgeClasses: Record<SourceStatus, string> = {
  DRAFT: 'bg-gray-100 text-gray-700',
  READY_FOR_REVIEW: 'bg-yellow-100 text-yellow-800',
  ACTIVE: 'bg-green-100 text-green-800',
  ARCHIVED: 'bg-slate-100 text-slate-600',
  DISABLED: 'bg-red-100 text-red-700',
};

const statusLabels: Record<SourceStatus, string> = {
  DRAFT: 'Bản nháp',
  READY_FOR_REVIEW: 'Chờ xét duyệt',
  ACTIVE: 'Đang hoạt động',
  ARCHIVED: 'Đã lưu trữ',
  DISABLED: 'Đã vô hiệu',
};

const approvalBadgeClasses: Record<ApprovalState, string> = {
  PENDING: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  APPROVED: 'bg-blue-50 text-blue-700 border-blue-200',
  REJECTED: 'bg-red-50 text-red-700 border-red-200',
};

const approvalLabels: Record<ApprovalState, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
};

const trustedBadgeClasses: Record<TrustedState, string> = {
  UNTRUSTED: 'bg-gray-50 text-gray-500 border-gray-200',
  TRUSTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REVOKED: 'bg-orange-50 text-orange-700 border-orange-200',
};

const trustedLabels: Record<TrustedState, string> = {
  UNTRUSTED: 'Chưa tin cậy',
  TRUSTED: 'Tin cậy',
  REVOKED: 'Đã thu hồi',
};

export const columns: ColumnDef<SourceSummaryResponse>[] = [
  {
    accessorKey: 'title',
    header: 'Tiêu đề / URL',
    cell: ({ row }) => <div className="max-w-[300px] truncate">{row.original.title}</div>,
  },
  {
    accessorKey: 'sourceType',
    header: 'Loại nguồn',
  },
  {
    accessorKey: 'status',
    header: 'Trạng thái',
    cell: ({ row }) => {
      const { status, approvalState, trustedState } = row.original;
      return (
        <div className="flex flex-col gap-1">
          <Badge className={statusBadgeClasses[status] ?? 'bg-gray-100 text-gray-600'}>
            {statusLabels[status] ?? status}
          </Badge>
          <div className="flex gap-1">
            <Badge variant="outline" className={approvalBadgeClasses[approvalState] ?? ''}>
              {approvalLabels[approvalState] ?? approvalState}
            </Badge>
            <Badge variant="outline" className={trustedBadgeClasses[trustedState] ?? ''}>
              {trustedLabels[trustedState] ?? trustedState}
            </Badge>
          </div>
        </div>
      );
    },
  },
  {
    accessorKey: 'createdAt',
    header: 'Ngày tạo',
    cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('vi-VN'),
  },
  {
    id: 'ingestion',
    header: 'Tiến trình',
    cell: ({ row }) => (
      <SourceIngestionDetailButton sourceId={row.original.id} sourceTitle={row.original.title} />
    ),
  },
  {
    id: 'actions',
    header: 'Hành động',
    cell: ({ row }) => <SourceActionsMenu source={row.original} />,
  },
];
