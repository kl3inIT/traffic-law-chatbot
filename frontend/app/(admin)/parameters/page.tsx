'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import {
  useParameterSets,
  useCreateParameterSet,
  useUpdateParameterSet,
  useActivateParameterSet,
  useCopyParameterSet,
  useDeleteParameterSet,
} from '@/hooks/use-parameters';
import type { AiParameterSetResponse } from '@/types/api';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
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

const schema = z.object({
  name: z.string().min(1, 'Tên bộ tham số là bắt buộc'),
  content: z.string().min(1, 'Nội dung YAML là bắt buộc'),
});
type FormValues = z.infer<typeof schema>;

export default function ParametersPage() {
  const { data: parameterSets, isLoading } = useParameterSets();
  const [selected, setSelected] = useState<AiParameterSetResponse | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AiParameterSetResponse | null>(null);

  const createMutation = useCreateParameterSet();
  const updateMutation = useUpdateParameterSet();
  const activate = useActivateParameterSet();
  const copy = useCopyParameterSet();
  const deleteMutation = useDeleteParameterSet();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', content: '' },
  });

  const openNew = () => {
    setSelected(null);
    setIsNew(true);
    form.reset({ name: '', content: '' });
  };

  const openEdit = (ps: AiParameterSetResponse) => {
    setSelected(ps);
    setIsNew(false);
    form.reset({ name: ps.name, content: ps.content });
  };

  const onSubmit = async (values: FormValues) => {
    if (isNew) {
      await createMutation.mutateAsync({ name: values.name, content: values.content });
      setIsNew(false);
      form.reset();
    } else if (selected) {
      await updateMutation.mutateAsync({ id: selected.id, data: { name: values.name, content: values.content } });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;
  const showEditor = isNew || selected !== null;

  return (
    <div className="p-6 flex flex-col gap-4 h-screen">
      <div className="flex items-center justify-between flex-shrink-0">
        <h1 className="text-xl font-semibold">Bộ tham số AI</h1>
        <Button onClick={openNew}>+ Tạo mới</Button>
      </div>

      <div className="flex gap-4 flex-1 min-h-0">
        {/* Danh sách bên trái */}
        <div className="w-64 flex-shrink-0 border rounded-lg overflow-y-auto">
          {isLoading && (
            <div className="p-3 space-y-2">
              {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
            </div>
          )}
          {parameterSets?.map((ps) => (
            <div
              key={ps.id}
              onClick={() => openEdit(ps)}
              className={cn(
                'p-3 border-b cursor-pointer hover:bg-muted transition-colors',
                selected?.id === ps.id && !isNew && 'bg-primary/10 border-l-2 border-l-primary'
              )}
            >
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm font-medium truncate">{ps.name}</span>
                {ps.active && <Badge className="text-xs">Đang hoạt động</Badge>}
              </div>
              <div className="flex gap-1 mt-2" onClick={(e) => e.stopPropagation()}>
                {!ps.active && (
                  <Button size="sm" variant="outline" className="h-6 text-xs px-2"
                    onClick={() => activate.mutate(ps.id)} disabled={activate.isPending}>
                    Kích hoạt
                  </Button>
                )}
                <Button size="sm" variant="outline" className="h-6 text-xs px-2"
                  onClick={() => copy.mutate(ps.id)} disabled={copy.isPending}>
                  Sao chép
                </Button>
                {!ps.active && (
                  <Button size="sm" variant="outline"
                    className="h-6 text-xs px-2 text-destructive border-destructive hover:bg-destructive hover:text-destructive-foreground"
                    onClick={() => setDeleteTarget(ps)}>
                    Xóa
                  </Button>
                )}
              </div>
            </div>
          ))}
          {!isLoading && parameterSets?.length === 0 && (
            <p className="p-4 text-sm text-muted-foreground text-center">Chưa có bộ tham số</p>
          )}
        </div>

        {/* Editor bên phải */}
        <div className="flex-1 border rounded-lg overflow-hidden flex flex-col">
          {!showEditor ? (
            <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
              Chọn bộ tham số để chỉnh sửa hoặc nhấn "+ Tạo mới"
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col h-full p-4 gap-4">
              <div className="space-y-1 flex-shrink-0">
                <Label htmlFor="name">Tên bộ tham số</Label>
                <Input id="name" {...form.register('name')} placeholder="Nhập tên bộ tham số" />
                {form.formState.errors.name && (
                  <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
                )}
              </div>

              <div className="flex flex-col flex-1 min-h-0 space-y-1">
                <Label htmlFor="content">Cấu hình YAML</Label>
                <Textarea
                  id="content"
                  {...form.register('content')}
                  className="font-mono text-sm flex-1 resize-none"
                  placeholder={"model:\n  name: claude-sonnet-4-6\n  temperature: 0.3\n  maxTokens: 2048\nretrieval:\n  topK: 5\n  similarityThreshold: 0.7\n  groundingLimitedThreshold: 0.5\nsystemPrompt: |\n  ..."}
                />
                {form.formState.errors.content && (
                  <p className="text-xs text-destructive">{form.formState.errors.content.message}</p>
                )}
              </div>

              {isError && (
                <Alert variant="destructive" className="flex-shrink-0">
                  <AlertDescription>Thao tác không thành công. Vui lòng thử lại.</AlertDescription>
                </Alert>
              )}

              <div className="flex gap-2 justify-end flex-shrink-0">
                <Button type="button" variant="outline"
                  onClick={() => { setSelected(null); setIsNew(false); form.reset(); }}>
                  Hủy
                </Button>
                <Button type="submit" disabled={isPending}>
                  {isPending ? 'Đang xử lý...' : isNew ? 'Tạo mới' : 'Lưu thay đổi'}
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>

      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Xóa bộ tham số "{deleteTarget?.name}"? Dữ liệu sẽ bị xóa vĩnh viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                if (deleteTarget) {
                  deleteMutation.mutate(deleteTarget.id);
                  if (selected?.id === deleteTarget.id) setSelected(null);
                  setDeleteTarget(null);
                }
              }}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
