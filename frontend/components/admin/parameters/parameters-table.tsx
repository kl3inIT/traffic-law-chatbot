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
            Chỉnh sửa
          </DropdownMenuItem>
          <DropdownMenuItem onClick={handleCopy}>
            Sao chép
          </DropdownMenuItem>
          {!paramSet.active && (
            <DropdownMenuItem onClick={() => activate.mutate(paramSet.id)}>
              <span className="text-primary">Kích hoạt</span>
            </DropdownMenuItem>
          )}
          {!paramSet.active && (
            <DropdownMenuItem
              className="text-destructive"
              onClick={() => setShowDeleteConfirm(true)}
            >
              Xóa
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Xác nhận xóa bộ tham số này? Dữ liệu sẽ bị xóa vĩnh viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                deleteMutation.mutate(paramSet.id);
                setShowDeleteConfirm(false);
              }}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
