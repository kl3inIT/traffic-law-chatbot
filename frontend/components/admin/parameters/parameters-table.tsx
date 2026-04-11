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
import type { AiParameterSetResponse } from '@/types/api';
import {
  useActivateParameterSet,
  useCopyParameterSet,
  useDeleteParameterSet,
} from '@/hooks/use-parameters';

interface ParameterActionsMenuProps {
  paramSet: AiParameterSetResponse;
  onEdit: (paramSet: AiParameterSetResponse) => void;
}

export function ParameterActionsMenu({ paramSet, onEdit }: ParameterActionsMenuProps) {
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const activate = useActivateParameterSet();
  const copy = useCopyParameterSet();
  const deleteMutation = useDeleteParameterSet();

  // Copy calls backend copy endpoint directly per D-14 and D-10.
  // No dialog-based fake create -- copy.mutate() calls POST /{id}/copy
  // which creates a server-side copy with " (ban sao)" suffix.
  const handleCopy = () => {
    copy.mutate(paramSet.id);
  };

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon" />}>
          <MoreHorizontal className="h-4 w-4" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => onEdit(paramSet)}>
            Chinh sua
          </DropdownMenuItem>
          <DropdownMenuItem onClick={handleCopy}>
            Sao chep
          </DropdownMenuItem>
          {!paramSet.active && (
            <DropdownMenuItem onClick={() => activate.mutate(paramSet.id)}>
              <span className="text-primary">Kich hoat</span>
            </DropdownMenuItem>
          )}
          {!paramSet.active && (
            <DropdownMenuItem
              className="text-destructive"
              onClick={() => setShowDeleteConfirm(true)}
            >
              Xoa
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xac nhan xoa</AlertDialogTitle>
            <AlertDialogDescription>
              Xac nhan xoa bo tham so nay? Du lieu se bi xoa vinh vien.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Huy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                deleteMutation.mutate(paramSet.id);
                setShowDeleteConfirm(false);
              }}
            >
              Xoa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
