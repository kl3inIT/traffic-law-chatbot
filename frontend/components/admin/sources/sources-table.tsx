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
import type { SourceSummaryResponse } from '@/types/api';
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

function buildActions(
  source: SourceSummaryResponse,
  approve: { mutate: (id: string) => void },
  reject: { mutate: (id: string) => void },
  activate: { mutate: (id: string) => void },
  deactivate: { mutate: (id: string) => void },
  reingest: { mutate: (id: string) => void },
): SourceAction[] {
  const actions: SourceAction[] = [];
  const { status, approvalState } = source;

  // Approval actions: only when approvalState is PENDING
  if (approvalState === 'PENDING') {
    actions.push({ label: 'Phê duyệt', action: (id) => approve.mutate(id) });
    actions.push({
      label: 'Từ chối',
      action: (id) => reject.mutate(id),
      destructive: true,
      confirmMessage: 'Xác nhận từ chối nguồn này? Thao tác này không thể hoàn tác.',
    });
  }

  // Activation: only when approved but not yet active
  if (approvalState === 'APPROVED' && status !== 'ACTIVE') {
    actions.push({ label: 'Kích hoạt', action: (id) => activate.mutate(id) });
  }

  // Deactivation: only when currently active
  if (status === 'ACTIVE') {
    actions.push({
      label: 'Hủy kích hoạt',
      action: (id) => deactivate.mutate(id),
      destructive: true,
      confirmMessage: 'Xác nhận hủy kích hoạt nguồn này? Nguồn sẽ bị loại khỏi truy xuất.',
    });
  }

  // Reingest available when not a fresh DRAFT waiting for first approval
  if (status !== 'DRAFT' || approvalState === 'REJECTED') {
    actions.push({ label: 'Nhập lại', action: (id) => reingest.mutate(id) });
  }

  return actions;
}

export function SourceActionsMenu({ source }: { source: SourceSummaryResponse }) {
  const [confirmAction, setConfirmAction] = useState<SourceAction | null>(null);

  const approve = useApproveSource();
  const reject = useRejectSource();
  const activate = useActivateSource();
  const deactivate = useDeactivateSource();
  const reingest = useReingestSource();

  const actions = buildActions(source, approve, reject, activate, deactivate, reingest);

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
            <AlertDialogDescription>{confirmAction?.confirmMessage}</AlertDialogDescription>
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
