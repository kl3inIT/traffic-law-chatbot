'use client';

import { useState } from 'react';
import { MoreHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import type { SourceSummaryResponse, SourceStatus } from '@/types/api';
import {
  useApproveSource,
  useRejectSource,
  useActivateSource,
  useDeactivateSource,
  useReingestSource,
} from '@/hooks/use-sources';

type SourceAction = {
  label: string;
  action: (id: string) => void;
  destructive?: boolean;
  confirmMessage?: string;
};

export function SourceActionsMenu({ source }: { source: SourceSummaryResponse }) {
  const [confirmAction, setConfirmAction] = useState<SourceAction | null>(null);

  const approve = useApproveSource();
  const reject = useRejectSource();
  const activate = useActivateSource();
  const deactivate = useDeactivateSource();
  const reingest = useReingestSource();

  const actionsByStatus: Record<SourceStatus, SourceAction[]> = {
    PENDING: [
      { label: 'Phê duyệt', action: (id) => approve.mutate(id) },
      {
        label: 'Từ chối',
        action: (id) => reject.mutate(id),
        destructive: true,
        confirmMessage: 'Xác nhận từ chối nguồn này? Thao tác này không thể hoàn tác.',
      },
    ],
    APPROVED: [
      { label: 'Kích hoạt', action: (id) => activate.mutate(id) },
      {
        label: 'Từ chối',
        action: (id) => reject.mutate(id),
        destructive: true,
        confirmMessage: 'Xác nhận từ chối nguồn này? Thao tác này không thể hoàn tác.',
      },
      { label: 'Nhập lại', action: (id) => reingest.mutate(id) },
    ],
    ACTIVE: [
      {
        label: 'Hủy kích hoạt',
        action: (id) => deactivate.mutate(id),
        destructive: true,
        confirmMessage: 'Xác nhận hủy kích hoạt nguồn này? Nguồn sẽ bị loại khỏi truy xuất.',
      },
      { label: 'Nhập lại', action: (id) => reingest.mutate(id) },
    ],
    REJECTED: [
      { label: 'Nhập lại', action: (id) => reingest.mutate(id) },
    ],
  };

  const actions = actionsByStatus[source.status] ?? [];

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" />}>
          <MoreHorizontal className="h-4 w-4" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {actions.map((act) => (
            <DropdownMenuItem
              key={act.label}
              className={act.destructive ? 'text-destructive' : undefined}
              onClick={() => {
                if (act.destructive && act.confirmMessage) {
                  setConfirmAction(act);
                } else {
                  act.action(source.id);
                }
              }}
            >
              {act.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={!!confirmAction} onOpenChange={() => setConfirmAction(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận thao tác</AlertDialogTitle>
            <AlertDialogDescription>
              {confirmAction?.confirmMessage}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                confirmAction?.action(source.id);
                setConfirmAction(null);
              }}
            >
              Xác nhận
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
