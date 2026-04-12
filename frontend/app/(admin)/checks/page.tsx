'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Play, Pencil, Trash2 } from 'lucide-react';
import { type ColumnDef } from '@tanstack/react-table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
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
import { DataTable } from '@/components/admin/data-table';
import {
  useCheckDefs,
  useCreateCheckDef,
  useUpdateCheckDef,
  useDeleteCheckDef,
} from '@/hooks/use-check-defs';
import { useTriggerCheckRun } from '@/hooks/use-check-runs';
import type { CheckDef } from '@/types/api';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

// ─── Form schema ──────────────────────────────────────────────────────────────

const schema = z.object({
  question: z.string().min(10, 'Tối thiểu 10 ký tự'),
  referenceAnswer: z.string().min(10, 'Tối thiểu 10 ký tự'),
  category: z.string().optional(),
  active: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

// ─── Table columns ────────────────────────────────────────────────────────────

function useColumns(
  onToggleActive: (def: CheckDef) => void,
  onEdit: (def: CheckDef) => void,
  onDelete: (def: CheckDef) => void,
  togglingIds: Set<string>,
): ColumnDef<CheckDef>[] {
  return [
    {
      id: 'select',
      header: ({ table }) => (
        <Checkbox
          checked={table.getIsAllPageRowsSelected()}
          indeterminate={table.getIsSomePageRowsSelected()}
          onCheckedChange={(v) => table.toggleAllPageRowsSelected(!!v)}
          aria-label="Chọn tất cả"
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={(v) => row.toggleSelected(!!v)}
          aria-label="Chọn hàng"
          onClick={(e) => e.stopPropagation()}
        />
      ),
      enableSorting: false,
      size: 40,
    },
    {
      accessorKey: 'question',
      header: 'Câu hỏi',
      cell: ({ row }) => (
        <span className="line-clamp-2 max-w-sm text-sm">{row.original.question}</span>
      ),
    },
    {
      accessorKey: 'category',
      header: 'Danh mục',
      cell: ({ row }) =>
        row.original.category ? (
          <Badge variant="secondary" className="text-xs">
            {row.original.category}
          </Badge>
        ) : (
          <span className="text-muted-foreground text-xs">—</span>
        ),
      size: 140,
    },
    {
      accessorKey: 'active',
      header: 'Bật',
      cell: ({ row }) => {
        const def = row.original;
        return (
          <Checkbox
            checked={def.active}
            disabled={togglingIds.has(def.id)}
            onCheckedChange={() => onToggleActive(def)}
            onClick={(e) => e.stopPropagation()}
            aria-label={def.active ? 'Đang hoạt động' : 'Đã tắt'}
          />
        );
      },
      size: 60,
    },
    {
      accessorKey: 'createdAt',
      header: 'Ngày tạo',
      cell: ({ row }) => (
        <span className="text-muted-foreground text-xs">
          {new Date(row.original.createdAt).toLocaleDateString('vi-VN')}
        </span>
      ),
      size: 100,
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => {
        const def = row.original;
        return (
          <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
            <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => onEdit(def)}>
              <Pencil className="h-3.5 w-3.5" />
            </Button>
            <Button
              size="icon"
              variant="ghost"
              className="text-destructive hover:text-destructive h-7 w-7"
              onClick={() => onDelete(def)}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        );
      },
      size: 80,
    },
  ];
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function ChecksPage() {
  const router = useRouter();
  const { data: checkDefs, isLoading } = useCheckDefs();

  const createMutation = useCreateCheckDef();
  const updateMutation = useUpdateCheckDef();
  const deleteMutation = useDeleteCheckDef();
  const triggerMutation = useTriggerCheckRun();

  // Instant active toggle — tracks which rows are mid-request
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());
  const handleToggleActive = useCallback(
    (def: CheckDef) => {
      setTogglingIds((prev) => new Set(prev).add(def.id));
      updateMutation.mutate(
        {
          id: def.id,
          payload: {
            question: def.question,
            referenceAnswer: def.referenceAnswer,
            category: def.category ?? undefined,
            active: !def.active,
          },
        },
        {
          onSettled: () =>
            setTogglingIds((prev) => {
              const next = new Set(prev);
              next.delete(def.id);
              return next;
            }),
        },
      );
    },
    [updateMutation],
  );

  // Bulk selection
  const [selectedRows, setSelectedRows] = useState<CheckDef[]>([]);

  const handleBulkSetActive = (active: boolean) => {
    selectedRows.forEach((def) => {
      if (def.active !== active) {
        setTogglingIds((prev) => new Set(prev).add(def.id));
        updateMutation.mutate(
          {
            id: def.id,
            payload: {
              question: def.question,
              referenceAnswer: def.referenceAnswer,
              category: def.category ?? undefined,
              active,
            },
          },
          {
            onSettled: () =>
              setTogglingIds((prev) => {
                const next = new Set(prev);
                next.delete(def.id);
                return next;
              }),
          },
        );
      }
    });
  };

  const handleBulkDelete = () => {
    selectedRows.forEach((def) => deleteMutation.mutate(def.id));
    setSelectedRows([]);
    setBulkDeleteOpen(false);
  };

  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);

  // Edit dialog
  const [editTarget, setEditTarget] = useState<CheckDef | null>(null);
  const [isNew, setIsNew] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { question: '', referenceAnswer: '', category: '', active: true },
  });

  const openNew = () => {
    setEditTarget(null);
    setIsNew(true);
    reset({ question: '', referenceAnswer: '', category: '', active: true });
  };

  const openEdit = (def: CheckDef) => {
    setEditTarget(def);
    setIsNew(false);
    reset({
      question: def.question,
      referenceAnswer: def.referenceAnswer,
      category: def.category ?? '',
      active: def.active,
    });
  };

  const closeDialog = () => {
    setEditTarget(null);
    setIsNew(false);
  };

  const onSubmit = async (values: FormValues) => {
    const payload = {
      question: values.question,
      referenceAnswer: values.referenceAnswer,
      category: values.category || undefined,
      active: values.active,
    };
    if (isNew) {
      await createMutation.mutateAsync(payload);
    } else if (editTarget) {
      await updateMutation.mutateAsync({ id: editTarget.id, payload });
    }
    closeDialog();
  };

  // Delete single
  const [deleteTarget, setDeleteTarget] = useState<CheckDef | null>(null);
  const handleDeleteSingle = async () => {
    if (!deleteTarget) return;
    await deleteMutation.mutateAsync(deleteTarget.id);
    setDeleteTarget(null);
  };

  // Run check
  const hasActiveDefs = (checkDefs ?? []).some((d) => d.active);
  const handleTrigger = async () => {
    await triggerMutation.mutateAsync();
    router.push('/checks/runs');
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;

  const columns = useColumns(handleToggleActive, openEdit, setDeleteTarget, togglingIds);

  return (
    <div className="flex h-screen flex-col gap-4 p-6">
      {/* Header */}
      <div className="flex flex-shrink-0 items-center justify-between">
        <h1 className="text-xl font-semibold">Định nghĩa kiểm tra</h1>
        <div className="flex gap-2">
          <Button
            variant="outline"
            disabled={!hasActiveDefs || triggerMutation.isPending}
            onClick={handleTrigger}
          >
            <Play className="mr-1 h-4 w-4" />
            {triggerMutation.isPending ? 'Đang chạy...' : 'Chạy kiểm tra'}
          </Button>
          <Button onClick={openNew}>+ Tạo mới</Button>
        </div>
      </div>

      {/* Bulk action bar */}
      {selectedRows.length > 0 && (
        <div className="bg-muted flex flex-shrink-0 items-center gap-3 rounded-lg border px-4 py-2 text-sm">
          <span className="font-medium">{selectedRows.length} câu hỏi đã chọn</span>
          <div className="ml-auto flex gap-2">
            <Button size="sm" variant="outline" onClick={() => handleBulkSetActive(true)}>
              Bật tất cả
            </Button>
            <Button size="sm" variant="outline" onClick={() => handleBulkSetActive(false)}>
              Tắt tất cả
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="text-destructive border-destructive"
              onClick={() => setBulkDeleteOpen(true)}
            >
              Xóa tất cả
            </Button>
          </div>
        </div>
      )}

      {/* Data table */}
      <div className="min-h-0 flex-1 overflow-auto">
        <DataTable
          columns={columns}
          data={checkDefs ?? []}
          isLoading={isLoading}
          enableRowSelection
          onSelectionChange={setSelectedRows}
        />
      </div>

      {/* Edit / create dialog */}
      <Dialog open={isNew || editTarget !== null} onOpenChange={(open) => !open && closeDialog()}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {isNew ? 'Tạo định nghĩa kiểm tra' : 'Chỉnh sửa định nghĩa kiểm tra'}
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1">
              <Label>Câu hỏi</Label>
              <Textarea
                rows={3}
                {...register('question')}
                placeholder="Nhập câu hỏi kiểm tra (tối thiểu 10 ký tự)"
              />
              {errors.question && (
                <p className="text-destructive text-xs">{errors.question.message}</p>
              )}
            </div>

            <div className="space-y-1">
              <Label>Câu trả lời tham chiếu</Label>
              <Textarea
                rows={4}
                {...register('referenceAnswer')}
                placeholder="Nhập câu trả lời mẫu (tối thiểu 10 ký tự)"
              />
              {errors.referenceAnswer && (
                <p className="text-destructive text-xs">{errors.referenceAnswer.message}</p>
              )}
            </div>

            <div className="space-y-1">
              <Label>Danh mục</Label>
              <Input {...register('category')} placeholder="Tuỳ chọn — vd: Phạt nguội, Thủ tục" />
            </div>

            <div className="flex items-center gap-2">
              <Controller
                name="active"
                control={control}
                render={({ field }) => (
                  <Checkbox id="active" checked={field.value} onCheckedChange={field.onChange} />
                )}
              />
              <Label htmlFor="active">Đang hoạt động</Label>
            </div>

            {isError && (
              <Alert variant="destructive">
                <AlertDescription>Thao tác không thành công. Vui lòng thử lại.</AlertDescription>
              </Alert>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeDialog}>
                Hủy
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? 'Đang xử lý...' : isNew ? 'Tạo mới' : 'Lưu thay đổi'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Single delete confirm */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Xóa câu hỏi &quot;{deleteTarget?.question.slice(0, 60)}&quot;? Dữ liệu sẽ bị xóa vĩnh
              viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Giữ lại</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={handleDeleteSingle}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Bulk delete confirm */}
      <AlertDialog open={bulkDeleteOpen} onOpenChange={setBulkDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa hàng loạt</AlertDialogTitle>
            <AlertDialogDescription>
              Xóa {selectedRows.length} câu hỏi đã chọn? Dữ liệu sẽ bị xóa vĩnh viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Giữ lại</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={handleBulkDelete}
            >
              Xóa tất cả
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
