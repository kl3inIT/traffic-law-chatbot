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

// Context-sensitive actions per UI-SPEC
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

  // Actions valid per status (per UI-SPEC D-10)
  const actionsByStatus: Record<SourceStatus, SourceAction[]> = {
    PENDING: [
      { label: 'Phe duyet', action: (id) => approve.mutate(id) },
      {
        label: 'Tu choi',
        action: (id) => reject.mutate(id),
        destructive: true,
        confirmMessage: 'Xac nhan tu choi nguon nay? Thao tac nay khong the hoan tac.',
      },
    ],
    APPROVED: [
      { label: 'Kich hoat', action: (id) => activate.mutate(id) },
      {
        label: 'Tu choi',
        action: (id) => reject.mutate(id),
        destructive: true,
        confirmMessage: 'Xac nhan tu choi nguon nay? Thao tac nay khong the hoan tac.',
      },
      { label: 'Nhap lai', action: (id) => reingest.mutate(id) },
    ],
    ACTIVE: [
      {
        label: 'Huy kich hoat',
        action: (id) => deactivate.mutate(id),
        destructive: true,
        confirmMessage: 'Xac nhan huy kich hoat nguon nay? Nguon se bi loai khoi truy xuat.',
      },
      { label: 'Nhap lai', action: (id) => reingest.mutate(id) },
    ],
    REJECTED: [
      { label: 'Nhap lai', action: (id) => reingest.mutate(id) },
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

      {/* Destructive confirmation AlertDialog */}
      <AlertDialog open={!!confirmAction} onOpenChange={() => setConfirmAction(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xac nhan thao tac</AlertDialogTitle>
            <AlertDialogDescription>
              {confirmAction?.confirmMessage}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Huy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                confirmAction?.action(source.id);
                setConfirmAction(null);
              }}
            >
              Xac nhan
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
