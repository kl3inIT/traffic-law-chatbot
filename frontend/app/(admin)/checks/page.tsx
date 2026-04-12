'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Play } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { Checkbox } from '@/components/ui/checkbox';
import { cn } from '@/lib/utils';
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
import { useCheckDefs, useCreateCheckDef, useUpdateCheckDef, useDeleteCheckDef } from '@/hooks/use-check-defs';
import { useTriggerCheckRun } from '@/hooks/use-check-runs';
import type { CheckDef } from '@/types/api';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  question: z.string().min(10, 'Tối thiểu 10 ký tự'),
  referenceAnswer: z.string().min(10, 'Tối thiểu 10 ký tự'),
  category: z.string().optional(),
  active: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export default function ChecksPage() {
  const router = useRouter();
  const { data: checkDefs, isLoading } = useCheckDefs();
  const [selected, setSelected] = useState<CheckDef | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const createMutation = useCreateCheckDef();
  const updateMutation = useUpdateCheckDef();
  const deleteMutation = useDeleteCheckDef();
  const triggerMutation = useTriggerCheckRun();

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

  const hasActiveDefs = (checkDefs ?? []).filter((d) => d.active).length > 0;
  const showEditor = isNew || selected !== null;

  const handleNew = () => {
    setSelected(null);
    setIsNew(true);
    reset({ question: '', referenceAnswer: '', category: '', active: true });
  };

  const handleSelect = (def: CheckDef) => {
    setSelected(def);
    setIsNew(false);
    reset({
      question: def.question,
      referenceAnswer: def.referenceAnswer,
      category: def.category ?? '',
      active: def.active,
    });
  };

  const handleCancel = () => {
    setSelected(null);
    setIsNew(false);
    reset();
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
      setIsNew(false);
      reset();
    } else if (selected) {
      await updateMutation.mutateAsync({ id: selected.id, payload });
    }
  };

  const handleTrigger = async () => {
    await triggerMutation.mutateAsync();
    router.push('/checks/runs');
  };

  const handleDelete = async () => {
    if (!selected) return;
    await deleteMutation.mutateAsync(selected.id);
    setSelected(null);
    setDeleteOpen(false);
    reset();
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;

  return (
    <div className="p-6 flex flex-col gap-4 h-screen">
      <div className="flex items-center justify-between flex-shrink-0">
        <h1 className="text-xl font-semibold">Định nghĩa kiểm tra</h1>
        <div className="flex gap-2">
          <Button
            variant="outline"
            disabled={!hasActiveDefs || triggerMutation.isPending}
            onClick={handleTrigger}
          >
            <Play className="h-4 w-4 mr-1" />
            {triggerMutation.isPending ? 'Đang chạy...' : 'Chạy kiểm tra'}
          </Button>
          <Button onClick={handleNew}>+ Tạo mới</Button>
        </div>
      </div>

      <div className="flex gap-4 flex-1 min-h-0">
        {/* Left panel */}
        <div className="w-64 flex-shrink-0 border rounded-lg overflow-y-auto">
          {isLoading && (
            <div className="p-3 space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          )}
          {(checkDefs ?? []).map((def) => (
            <div
              key={def.id}
              onClick={() => handleSelect(def)}
              className={cn(
                'p-3 border-b cursor-pointer hover:bg-muted transition-colors',
                selected?.id === def.id && !isNew && 'bg-primary/10 border-l-2 border-l-primary',
              )}
            >
              <p className="text-sm font-medium truncate">{def.question}</p>
              <div className="flex items-center gap-2 mt-1">
                {def.category && (
                  <span className="text-xs text-muted-foreground">{def.category}</span>
                )}
                <Badge variant="outline" className="text-xs">
                  {def.active ? 'Đang hoạt động' : 'Tắt'}
                </Badge>
              </div>
            </div>
          ))}
          {!isLoading && (checkDefs ?? []).length === 0 && (
            <p className="p-4 text-sm text-muted-foreground text-center">
              Chưa có định nghĩa kiểm tra
            </p>
          )}
        </div>

        {/* Right panel */}
        <div className="flex-1 border rounded-lg overflow-hidden flex flex-col">
          {!showEditor ? (
            <div className="flex items-center justify-center h-full text-sm text-muted-foreground">
              Chọn định nghĩa để chỉnh sửa hoặc nhấn &apos;+ Tạo mới&apos;
            </div>
          ) : (
            <form
              onSubmit={handleSubmit(onSubmit)}
              className="flex flex-col h-full p-4 gap-4 overflow-y-auto"
            >
              <div className="space-y-1">
                <Label>Câu hỏi</Label>
                <Textarea rows={3} {...register('question')} placeholder="Nhập câu hỏi kiểm tra (tối thiểu 10 ký tự)" />
                {errors.question && (
                  <p className="text-xs text-destructive">{errors.question.message}</p>
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
                  <p className="text-xs text-destructive">{errors.referenceAnswer.message}</p>
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
                    <Checkbox
                      id="active"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  )}
                />
                <Label htmlFor="active">Đang hoạt động</Label>
              </div>

              {isError && (
                <Alert variant="destructive">
                  <AlertDescription>Thao tác không thành công. Vui lòng thử lại.</AlertDescription>
                </Alert>
              )}

              <div className="flex gap-2 justify-between mt-auto">
                {selected && !isNew && (
                  <Button
                    type="button"
                    variant="outline"
                    className="text-destructive border-destructive hover:bg-destructive/10"
                    onClick={() => setDeleteOpen(true)}
                  >
                    Xóa
                  </Button>
                )}
                <div className="flex gap-2 ml-auto">
                  <Button type="button" variant="outline" onClick={handleCancel}>
                    Giữ lại
                  </Button>
                  <Button type="submit" disabled={isPending}>
                    {isPending ? 'Đang xử lý...' : isNew ? 'Tạo mới' : 'Lưu thay đổi'}
                  </Button>
                </div>
              </div>
            </form>
          )}
        </div>
      </div>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Xóa định nghĩa kiểm tra &apos;{selected?.question.slice(0, 40)}&apos;? Dữ liệu sẽ bị xóa vĩnh viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Giữ lại</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={handleDelete}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
